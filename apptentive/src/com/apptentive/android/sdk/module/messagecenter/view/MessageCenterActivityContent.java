/*
 * Copyright (c) 2015, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.module.messagecenter.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.apptentive.android.sdk.ApptentiveInternal;
import com.apptentive.android.sdk.Log;
import com.apptentive.android.sdk.model.Event;

import com.apptentive.android.sdk.module.ActivityContent;
import com.apptentive.android.sdk.module.messagecenter.MessageManager;
import com.apptentive.android.sdk.module.messagecenter.MessagePollingWorker;
import com.apptentive.android.sdk.module.messagecenter.model.MessageCenterListItem;
import com.apptentive.android.sdk.module.messagecenter.model.OutgoingFileMessage;
import com.apptentive.android.sdk.module.messagecenter.model.OutgoingTextMessage;
import com.apptentive.android.sdk.module.metric.MetricModule;
import com.apptentive.android.sdk.util.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Created by barryli on 6/23/15.
 */

public class MessageCenterActivityContent extends ActivityContent {
	private MessageCenterView messageCenterView;
	private Map<String, String> customData;
	private Context context;
	private MessageManager.OnNewMessagesListener newMessageListener;

	public MessageCenterActivityContent(Serializable data) {
		this.customData = (Map<String, String>) data;
	}

	@Override
	public void onCreate(Activity activity, Bundle onSavedInstanceState) {

		context = activity;
		MetricModule.sendMetric(context.getApplicationContext(), Event.EventLabel.message_center__launch);

		MessageCenterView.OnSendMessageListener onSendMessageListener = new MessageCenterView.OnSendMessageListener() {
			public void onSendTextMessage(String text) {
				final OutgoingTextMessage message = new OutgoingTextMessage();
				message.setBody(text);
				message.setRead(true);
				message.setCustomData(customData);

				MessageManager.sendMessage(context.getApplicationContext(), message);
				messageCenterView.post(new Runnable() {
					public void run() {
						messageCenterView.addItem(message);
						messageCenterView.onResumeSending();
						messageCenterView.scrollMessageListViewToBottom();
					}
				});
			}

			public void onSendFileMessage(Uri uri) {
				// First, create the file, and populate some metadata about it.
				final OutgoingFileMessage message = new OutgoingFileMessage();
				boolean successful = message.internalCreateStoredImage(context.getApplicationContext(), uri.toString());
				if (successful) {
					message.setRead(true);
					message.setCustomData(customData);

					// Finally, send out the message.
					MessageManager.sendMessage(context.getApplicationContext(), message);
					messageCenterView.post(new Runnable() {
						public void run() {
							messageCenterView.addItem(message);
							messageCenterView.onResumeSending();
							messageCenterView.scrollMessageListViewToBottom();
						}
					});
				} else {
					Log.e("Unable to send file.");
					Toast.makeText(messageCenterView.getContext(), "Unable to send file.", Toast.LENGTH_SHORT).show();
				}
			}
		};

		messageCenterView = new MessageCenterView(activity, onSendMessageListener);

		// Remove an existing MessageCenterView and replace it with this, if it exists.
		if (messageCenterView.getParent() != null) {
			((ViewGroup) messageCenterView.getParent()).removeView(messageCenterView);
		}
		activity.setContentView(messageCenterView);

		newMessageListener = new MessageManager.OnNewMessagesListener() {
			public void onMessagesUpdated() {
				messageCenterView.post(new Runnable() {
					public void run() {
						List<MessageCenterListItem> items = MessageManager.getMessageCenterListItems(context.getApplicationContext());
						messageCenterView.setItems(items);
						messageCenterView.scrollMessageListViewToBottom();
					}
				});
			}
		};

		// This listener will run when messages are retrieved from the server, and will start a new thread to update the view.
		MessageManager.addInternalOnMessagesUpdatedListener(newMessageListener);

		// Give the MessageCenterView a callback when a message is sent.
		MessageManager.setAfterSendMessageListener(messageCenterView);

		// Needed to prevent the window from being pushed up when a text input area is focused.
		Window window = activity.getWindow();
		window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		return;
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		return;
	}

	@Override
	public boolean onBackPressed(Activity activity) {
		clearPendingMessageCenterPushNotification();
		MetricModule.sendMetric(activity, Event.EventLabel.message_center__close);
		savePendingComposingMessage();
		// Set to null, otherwise they will hold reference to the activity context
		MessageManager.clearInternalOnMessagesUpdatedListeners();
		MessageManager.setAfterSendMessageListener(null);
		return true;
	}

	public void onStart() {
		MessagePollingWorker.setMessageCenterInForeground(true);
	}

	public void onStop() {
		clearPendingMessageCenterPushNotification();
		MessagePollingWorker.setMessageCenterInForeground(false);
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			switch (requestCode) {
				case Constants.REQUEST_CODE_PHOTO_FROM_MESSAGE_CENTER:
					messageCenterView.showAttachmentDialog(context, data.getData());
					break;
				default:
					break;
			}
		}
	}

	private void savePendingComposingMessage() {
		Editable content = messageCenterView.getPendingComposingContent();

		if (content != null) {
			SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = prefs.edit();
			editor.putString(Constants.PREF_KEY_MESSAGE_CENTER_PENDING_COMPOSING_MESSAGE, content.toString());
			editor.commit();
		}
	}

	private void clearPendingMessageCenterPushNotification() {
		SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		String pushData = prefs.getString(Constants.PREF_KEY_PENDING_PUSH_NOTIFICATION, null);
		if (pushData != null) {
			try {
				JSONObject pushJson = new JSONObject(pushData);
				ApptentiveInternal.PushAction action = ApptentiveInternal.PushAction.unknown;
				if (pushJson.has(ApptentiveInternal.PUSH_ACTION)) {
					action = ApptentiveInternal.PushAction.parse(pushJson.getString(ApptentiveInternal.PUSH_ACTION));
				}
				switch (action) {
					case pmc:
						Log.i("Clearing pending Message Center push notification.");
						prefs.edit().remove(Constants.PREF_KEY_PENDING_PUSH_NOTIFICATION).commit();
						break;
				}
			} catch (JSONException e) {
				Log.w("Error parsing JSON from push notification.", e);
				MetricModule.sendError(context.getApplicationContext(), e, "Parsing Push notification", pushData);
			}
		}
	}
}
