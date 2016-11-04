/*
 * Copyright (c) 2016, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.module.engagement.interaction.fragment;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.EditText;

import com.apptentive.android.sdk.Apptentive;
import com.apptentive.android.sdk.ApptentiveInternal;
import com.apptentive.android.sdk.ApptentiveLog;
import com.apptentive.android.sdk.ApptentiveViewActivity;
import com.apptentive.android.sdk.R;
import com.apptentive.android.sdk.comm.ApptentiveHttpResponse;
import com.apptentive.android.sdk.module.engagement.EngagementModule;
import com.apptentive.android.sdk.module.engagement.interaction.model.MessageCenterInteraction;
import com.apptentive.android.sdk.module.messagecenter.MessageManager;
import com.apptentive.android.sdk.module.messagecenter.OnListviewItemActionListener;
import com.apptentive.android.sdk.module.messagecenter.model.ApptentiveMessage;
import com.apptentive.android.sdk.module.messagecenter.model.CompoundMessage;
import com.apptentive.android.sdk.module.messagecenter.model.ContextMessage;
import com.apptentive.android.sdk.module.messagecenter.model.MessageCenterStatus;
import com.apptentive.android.sdk.module.messagecenter.model.MessageCenterUtil;
import com.apptentive.android.sdk.module.messagecenter.model.WhoCard;
import com.apptentive.android.sdk.module.messagecenter.view.AttachmentPreviewDialog;
import com.apptentive.android.sdk.module.messagecenter.view.MessageCenterListView;
import com.apptentive.android.sdk.module.messagecenter.view.MessageCenterRecyclerView;
import com.apptentive.android.sdk.module.messagecenter.view.MessageCenterRecyclerViewAdapter;
import com.apptentive.android.sdk.module.messagecenter.view.holder.MessageComposerHolder;
import com.apptentive.android.sdk.module.metric.MetricModule;
import com.apptentive.android.sdk.util.AnimationUtil;
import com.apptentive.android.sdk.util.Constants;
import com.apptentive.android.sdk.util.Util;
import com.apptentive.android.sdk.util.image.ApptentiveAttachmentLoader;
import com.apptentive.android.sdk.util.image.ApptentiveImageGridView;
import com.apptentive.android.sdk.util.image.ImageGridViewAdapter;
import com.apptentive.android.sdk.util.image.ImageItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import static com.apptentive.android.sdk.module.messagecenter.model.MessageCenterUtil.MessageCenterListItem.MESSAGE_COMPOSER;
import static com.apptentive.android.sdk.module.messagecenter.model.MessageCenterUtil.MessageCenterListItem.MESSAGE_CONTEXT;
import static com.apptentive.android.sdk.module.messagecenter.model.MessageCenterUtil.MessageCenterListItem.MESSAGE_OUTGOING;
import static com.apptentive.android.sdk.module.messagecenter.model.MessageCenterUtil.MessageCenterListItem.STATUS;
import static com.apptentive.android.sdk.module.messagecenter.model.MessageCenterUtil.MessageCenterListItem.WHO_CARD;

public class MessageCenterFragment extends ApptentiveBaseFragment<MessageCenterInteraction> implements
	OnListviewItemActionListener,
	MessageManager.AfterSendMessageListener,
	MessageManager.OnNewIncomingMessagesListener,
	OnMenuItemClickListener,
	AbsListView.OnScrollListener,
	MessageCenterListView.OnListviewResizeListener,
	ImageGridViewAdapter.Callback {

	private MenuItem profileMenuItem;
	private boolean bShowProfileMenuItem = true;

	// keys used to save instance in the event of rotation
	private final static String LIST_TOP_INDEX = "list_top_index";
	private final static String LIST_TOP_OFFSET = "list_top_offset";
	private final static String COMPOSING_EDITTEXT_STATE = "edittext";
	private final static String CONTEXT_MESSAGE = "context_message";
	private final static String COMPOSING_ATTACHMENTS = "attachments";
	private final static String WHO_CARD_MODE = "whocardmode";
	private final static String WHO_CARD_NAME = "whocardname";
	private final static String WHO_CARD_EMAIL = "whocardemail";
	private final static String WHO_CARD_AVATAR_FILE = "whocardavatar";

	private final static String DIALOG_IMAGE_PREVIEW = "imagePreviewDialog";

	private final static long DEFAULT_DELAYMILLIS = 200;

	/* Fragment.getActivity() may return null if not attached.
	 * hostingActivityRef is always set in onAttach()
	 * Keeping a cached weak reference ensures it's safe to use
	 */
	private WeakReference<Activity> hostingActivityRef;

	private MessageCenterRecyclerView messageCenterRecyclerView;

	// Holder and view references
	private MessageComposerHolder composer;
	private EditText composerEditText;
	private EditText whoCardNameEditText;
	private EditText whoCardEmailEditText;

	private View fab;

	private boolean forceShowKeyboard;

	// Data backing of the listview
	private ArrayList<MessageCenterUtil.MessageCenterListItem> messages = new ArrayList<MessageCenterUtil.MessageCenterListItem>();
	private MessageCenterRecyclerViewAdapter messageCenterRecyclerViewAdapter;

	// MesssageCenterView is set to paused when it fails to send message
	private boolean isPaused = false;
	// Count how many paused ongoing messages
	private int unsentMessagesCount = 0;

	// Data Item references
	private ContextMessage contextMessage;

	//private ArrayList<ImageItem> imageAttachmentstList = new ArrayList<ImageItem>();

	/**
	 * Used to save the state of the message text box if the user closes Message Center for a moment,
	 * , rotate device, attaches a file, etc.
	 */
	private Parcelable composingViewSavedState;
	private ArrayList<ImageItem> pendingAttachments = new ArrayList<ImageItem>();

	/*
	 * Set to true when user launches image picker, and set to false once an image is picked
	 * This is used to track if the user tried to attach an image but abandoned the image picker
	 * without picking anything
	 */
	private boolean imagePickerLaunched = false;

	/**
	 * Used to save the state of the who card if the user closes Message Center for a moment,
	 * , rotate device, attaches a file, etc.
	 */
	private boolean pendingWhoCardMode;
	private Parcelable pendingWhoCardName;
	private Parcelable pendingWhoCardEmail;
	private String pendingWhoCardAvatarFile;

	private int listViewSavedTopIndex = -1;
	private int listViewSavedTopOffset;

	// FAB y-offset in pixels from the bottom edge
	private int fabPaddingPixels;

	protected static final int MSG_SCROLL_TO_BOTTOM = 1;
	protected static final int MSG_SCROLL_FROM_TOP = 2;
	protected static final int MSG_MESSAGE_SENT = 3;
	protected static final int MSG_START_SENDING = 4;
	protected static final int MSG_PAUSE_SENDING = 5;
	protected static final int MSG_RESUME_SENDING = 6;
	protected static final int MSG_MESSAGE_ADD_INCOMING = 7;
	protected static final int MSG_MESSAGE_ADD_WHOCARD = 8;
	protected static final int MSG_MESSAGE_ADD_COMPOSING = 9;
	protected static final int MSG_SEND_CONTEXT_MESSAGE = 10;
	protected static final int MSG_REMOVE_COMPOSER = 11;
	protected static final int MSG_REMOVE_STATUS = 12;
	protected static final int MSG_OPT_INSERT_REGULAR_STATUS = 13;
	protected static final int MSG_MESSAGE_REMOVE_WHOCARD = 14;
	protected static final int MSG_ADD_CONTEXT_MESSAGE = 15;
	protected static final int MSG_ADD_GREETING = 16;

	private MessageCenterFragment.MessagingActionHandler messagingActionHandler;

	public static MessageCenterFragment newInstance(Bundle bundle) {
		MessageCenterFragment mcFragment = new MessageCenterFragment();
		mcFragment.setArguments(bundle);
		return mcFragment;
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Make Message Center fragment retain its instance on orientation change
		setRetainInstance(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		listViewSavedTopIndex = (savedInstanceState == null) ? -1 : savedInstanceState.getInt(LIST_TOP_INDEX);
		listViewSavedTopOffset = (savedInstanceState == null) ? 0 : savedInstanceState.getInt(LIST_TOP_OFFSET);
		composingViewSavedState = (savedInstanceState == null) ? null : savedInstanceState.getParcelable(COMPOSING_EDITTEXT_STATE);
		pendingWhoCardName = (savedInstanceState == null) ? null : savedInstanceState.getParcelable(WHO_CARD_NAME);
		pendingWhoCardEmail = (savedInstanceState == null) ? null : savedInstanceState.getParcelable(WHO_CARD_EMAIL);
		pendingWhoCardAvatarFile = (savedInstanceState == null) ? null : savedInstanceState.getString(WHO_CARD_AVATAR_FILE);
		pendingWhoCardMode = savedInstanceState != null && savedInstanceState.getBoolean(WHO_CARD_MODE);
		return inflater.inflate(R.layout.apptentive_message_center, container, false);
	}

	public void onViewCreated(View view, Bundle onSavedInstanceState) {
		ApptentiveLog.e("onViewCreated()");
		super.onViewCreated(view, onSavedInstanceState);
		boolean isInitialViewCreation = (onSavedInstanceState == null);
		/* When isInitialViewCreation is false, the view is being recreated after orientation change.
		 * Because the fragment is set to be retained after orientation change, setup() will reuse the retained states
		 */
		setup(view, isInitialViewCreation);

		MessageManager mgr = ApptentiveInternal.getInstance().getMessageManager();
		// This listener will run when messages are retrieved from the server, and will start a new thread to update the view.
		mgr.addInternalOnMessagesUpdatedListener(this);
		// Give the MessageCenterView a callback when a message is sent.
		mgr.setAfterSendMessageListener(this);


		// Needed to prevent the window from being pushed up when a text input area is focused.
		getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

		// Restore listview scroll offset to where it was before rotation
		if (listViewSavedTopIndex != -1) {
			messagingActionHandler.sendMessageDelayed(messagingActionHandler.obtainMessage(MSG_SCROLL_FROM_TOP, listViewSavedTopIndex, listViewSavedTopOffset), DEFAULT_DELAYMILLIS);
		} else {
			messagingActionHandler.sendEmptyMessageDelayed(MSG_SCROLL_TO_BOTTOM, DEFAULT_DELAYMILLIS);
		}
	}


	@Override
	public void onAttach(Context context) {
		super.onAttach(context);

		hostingActivityRef = new WeakReference<Activity>((Activity) context);
		messagingActionHandler = new MessageCenterFragment.MessagingActionHandler(this);
	}

	@Override
	public void onDetach() {
		super.onDetach();
		// messageCenterRecyclerViewAdapter holds a reference to fragment context through Cstor. Need to set it to null to prevent leak
		messageCenterRecyclerViewAdapter = null;
		messageCenterRecyclerView.setAdapter(null);
	}

	public void onStart() {
		super.onStart();
		ApptentiveInternal.getInstance().getMessageManager().setMessageCenterInForeground(true);
	}

	public void onStop() {
		super.onStop();
		clearPendingMessageCenterPushNotification();
		ApptentiveInternal.getInstance().getMessageManager().setMessageCenterInForeground(false);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			switch (requestCode) {
				case Constants.REQUEST_CODE_CLOSE_COMPOSING_CONFIRMATION: {
					onCancelComposing();
					break;
				}
				case Constants.REQUEST_CODE_PHOTO_FROM_SYSTEM_PICKER: {
					if (data == null) {
						ApptentiveLog.d("no image is picked");
						return;
					}
					imagePickerLaunched = false;
					Uri uri;
					Activity hostingActivity = hostingActivityRef.get();
					//Android SDK less than 19
					if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
						uri = data.getData();
					} else {
						//for Android 4.4
						uri = data.getData();
						int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
						if (hostingActivity != null) {
							hostingActivity.getContentResolver().takePersistableUriPermission(uri, takeFlags);
						}
					}


					String originalPath = Util.getRealFilePathFromUri(hostingActivity, uri);
					if (originalPath != null) {
						/* If able to retrieve file path and creation time from uri, cache file name will be generated
						 * from the md5 of file path + creation time
						 */
						long creation_time = Util.getContentCreationTime(hostingActivity, uri);
						Uri fileUri = Uri.fromFile(new File(originalPath));
						File cacheDir = Util.getDiskCacheDir(hostingActivity);
						addAttachmentsToComposer(Arrays.asList(new ImageItem(originalPath, Util.generateCacheFileFullPath(fileUri, cacheDir, creation_time),
							Util.getMimeTypeFromUri(hostingActivity, uri), creation_time)));
					} else {
						/* If not able to get image file path due to not having READ_EXTERNAL_STORAGE permission,
						 * cache name will be generated from md5 of uri string
						 */
						File cacheDir = Util.getDiskCacheDir(hostingActivity);
						String cachedFileName = Util.generateCacheFileFullPath(uri, cacheDir, 0);
						addAttachmentsToComposer(Arrays.asList(new ImageItem(uri.toString(), cachedFileName, Util.getMimeTypeFromUri(hostingActivity, uri), 0)));
					}

					break;
				}
				default:
					break;
			}
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		ApptentiveInternal.getInstance().getMessageManager().pauseSending(MessageManager.SEND_PAUSE_REASON_ACTIVITY_PAUSE);
	}

	@Override
	public void onResume() {
		super.onResume();
		ApptentiveInternal.getInstance().getMessageManager().resumeSending();

		/* imagePickerLaunched was set true when the picker intent was launched. If user had picked an image,
		 * it woud have been set to false. Otherwise, it indicates the user tried to attach an image but
		 * abandoned the image picker without picking anything
		 */
		if (imagePickerLaunched) {
			EngagementModule.engageInternal(hostingActivityRef.get(), interaction, MessageCenterInteraction.EVENT_NAME_ATTACHMENT_CANCEL);
			imagePickerLaunched = false;
		}
	}

	protected int getMenuResourceId() {
		return R.menu.apptentive_message_center;
	}

	@Override
	protected void attachFragmentMenuListeners(Menu menu) {
		profileMenuItem = menu.findItem(R.id.profile);
		profileMenuItem.setOnMenuItemClickListener(this);
		updateMenuVisibility();
	}

	@Override
	protected void updateMenuVisibility() {
		profileMenuItem.setVisible(bShowProfileMenuItem);
		profileMenuItem.setEnabled(bShowProfileMenuItem);
	}

	private void setup(View rootView, boolean isInitMessages) {
		boolean addedAnInteractiveCard = false;

		messageCenterRecyclerView = (MessageCenterRecyclerView) rootView.findViewById(R.id.message_center_recycler_view);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			messageCenterRecyclerView.setNestedScrollingEnabled(true);
		}
		LinearLayoutManager layoutManager = new LinearLayoutManager(this.getContext());
		layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
		messageCenterRecyclerView.setLayoutManager(layoutManager);

/*
		((MessageCenterListView) messageCenterListView).setOnListViewResizeListener(this);
		messageCenterListView.setItemsCanFocus(true);
*/


		fab = rootView.findViewById(R.id.composing_fab);
		fab.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				forceShowKeyboard = true;
				addComposingCard();
			}
		});

		messageCenterRecyclerViewAdapter = new MessageCenterRecyclerViewAdapter(this, this, interaction, messages);

		boolean showKeyboard = false;
		if (isInitMessages) {
			List<MessageCenterUtil.MessageCenterListItem> items = ApptentiveInternal.getInstance().getMessageManager().getMessageCenterListItems();
			if (items != null) {
				// populate message list from db
				prepareMessages(items);
			}

			String contextualMessageBody = interaction.getContextualMessageBody();
			if (contextualMessageBody != null) {
				// Clear any pending composing message to present an empty composing area
				clearPendingComposingMessage();
				messagingActionHandler.sendEmptyMessage(MSG_REMOVE_STATUS);
				messagingActionHandler.sendMessage(messagingActionHandler.obtainMessage(MSG_ADD_CONTEXT_MESSAGE, contextualMessageBody));
				// If checkAddWhoCardIfRequired returns true, it will add WhoCard, otherwise add composing card
				if (!checkAddWhoCardIfRequired()) {
					addedAnInteractiveCard = true;
					forceShowKeyboard = false;
					addComposingCard();
				}
			}

			/* Add who card with pending contents
			** Pending contents would be saved if the user was in composing Who card mode and exitted through back button
			 */
			else if (pendingWhoCardName != null || pendingWhoCardEmail != null || pendingWhoCardAvatarFile != null) {
				addedAnInteractiveCard = true;
				addWhoCard(pendingWhoCardMode);
			} else if (!checkAddWhoCardIfRequired()) {
				/* If there is only greeting message, show composing.
				 * If Who Card is required, show Who Card first
				 */
				if (messages.size() == 1) { // TODO: Don't use these magic numbers everywhere
					addedAnInteractiveCard = true;
					addComposingCard();
				} else {
					// Finally check if status message need to be restored
					addExpectationStatusIfNeeded();
				}
			}

			updateMessageSentStates(); // Force timestamp recompilation.

		}

		messageCenterRecyclerView.setAdapter(messageCenterRecyclerViewAdapter);

		// Calculate FAB y-offset
		fabPaddingPixels = calculateFabPadding(rootView.getContext());

		if (!addedAnInteractiveCard) {
			showFab();
		}

		// Retrieve any saved attachments
		final SharedPreferences prefs = ApptentiveInternal.getInstance().getSharedPrefs();
		if (prefs.contains(Constants.PREF_KEY_MESSAGE_CENTER_PENDING_COMPOSING_ATTACHMENTS)) {
			JSONArray savedAttachmentsJsonArray = null;
			try {
				savedAttachmentsJsonArray = new JSONArray(prefs.getString(Constants.PREF_KEY_MESSAGE_CENTER_PENDING_COMPOSING_ATTACHMENTS, ""));
			} catch (JSONException e) {
				e.printStackTrace();
			}
			if (savedAttachmentsJsonArray != null && savedAttachmentsJsonArray.length() > 0) {
				if (pendingAttachments == null) {
					pendingAttachments = new ArrayList<ImageItem>();
				} else {
					pendingAttachments.clear();
				}
				for (int i = 0; i < savedAttachmentsJsonArray.length(); i++) {
					try {
						JSONObject savedAttachmentJson = savedAttachmentsJsonArray.getJSONObject(i);
						if (savedAttachmentJson != null) {
							pendingAttachments.add(new ImageItem(savedAttachmentJson));
						}
					} catch (JSONException e) {
						continue;
					}
				}
			}
			// Stored pending attachemnts have been restored, remove it from the persistent storage
			SharedPreferences.Editor editor = prefs.edit();
			editor.remove(Constants.PREF_KEY_MESSAGE_CENTER_PENDING_COMPOSING_ATTACHMENTS).apply();
		}
	}

	public boolean onMenuItemClick(MenuItem menuItem) {
		int menuItemId = menuItem.getItemId();

		if (menuItemId == R.id.profile) {
			JSONObject data = new JSONObject();
			try {
				data.put("required", interaction.getWhoCardRequired());
				data.put("trigger", "button");
			} catch (JSONException e) {
				//
			}
			EngagementModule.engageInternal(hostingActivityRef.get(), interaction, MessageCenterInteraction.EVENT_NAME_PROFILE_OPEN, data.toString());

			final SharedPreferences prefs = ApptentiveInternal.getInstance().getSharedPrefs();
			boolean whoCardDisplayedBefore = wasWhoCardAsPreviouslyDisplayed();
			forceShowKeyboard = true;
			addWhoCard(!whoCardDisplayedBefore);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		savePendingComposingMessage();
		//int index = messageCenterRecyclerView.getFirstVisiblePosition();
		View v = messageCenterRecyclerView.getChildAt(0);
		int top = (v == null) ? 0 : (v.getTop() - messageCenterRecyclerView.getPaddingTop());
		//outState.putInt(LIST_TOP_INDEX, index);
		outState.putInt(LIST_TOP_OFFSET, top);
		outState.putParcelable(COMPOSING_EDITTEXT_STATE, saveEditTextInstanceState());
		if (messageCenterRecyclerViewAdapter != null) {
			outState.putParcelable(WHO_CARD_NAME, whoCardNameEditText != null ? whoCardNameEditText.onSaveInstanceState() : null);
			outState.putParcelable(WHO_CARD_EMAIL, whoCardEmailEditText != null ? whoCardEmailEditText.onSaveInstanceState() : null);
			outState.putString(WHO_CARD_AVATAR_FILE, messageCenterRecyclerViewAdapter.getWhoCardAvatarFileName());
		}
		outState.putBoolean(WHO_CARD_MODE, pendingWhoCardMode);
		super.onSaveInstanceState(outState);
	}

	public boolean onBackPressed(boolean hardwareButton) {
		savePendingComposingMessage();
		ApptentiveViewActivity hostingActivity = (ApptentiveViewActivity) hostingActivityRef.get();
		if (hostingActivity != null) {
			DialogFragment myFrag = (DialogFragment) (hostingActivity.getSupportFragmentManager()).findFragmentByTag(DIALOG_IMAGE_PREVIEW);
			if (myFrag != null) {
				myFrag.dismiss();
				myFrag = null;
			}
			cleanup();
			if (hardwareButton) {
				EngagementModule.engageInternal(hostingActivity, interaction, MessageCenterInteraction.EVENT_NAME_CANCEL);
			} else {
				EngagementModule.engageInternal(hostingActivity, interaction, MessageCenterInteraction.EVENT_NAME_CLOSE);
			}
		}
		return false;
	}

	public boolean cleanup() {
		clearPendingMessageCenterPushNotification();
		// Set to null, otherwise they will hold reference to the activity context
		MessageManager mgr = ApptentiveInternal.getInstance().getMessageManager();

		mgr.clearInternalOnMessagesUpdatedListeners();
		mgr.setAfterSendMessageListener(null);


		ApptentiveInternal.getInstance().getAndClearCustomData();
		ApptentiveAttachmentLoader.getInstance().clearMemoryCache();
		return true;
	}


	private void clearPendingMessageCenterPushNotification() {
		SharedPreferences prefs = ApptentiveInternal.getInstance().getSharedPrefs();
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
						ApptentiveLog.i("Clearing pending Message Center push notification.");
						prefs.edit().remove(Constants.PREF_KEY_PENDING_PUSH_NOTIFICATION).apply();
						break;
				}
			} catch (JSONException e) {
				ApptentiveLog.w("Error parsing JSON from push notification.", e);
				MetricModule.sendError(e, "Parsing Push notification", pushData);
			}
		}
	}

	public void addComposingCard() {
		hideFab();
		hideProfileButton();
		messagingActionHandler.removeMessages(MSG_MESSAGE_ADD_WHOCARD);
		messagingActionHandler.removeMessages(MSG_MESSAGE_ADD_COMPOSING);
		messagingActionHandler.sendEmptyMessage(MSG_REMOVE_STATUS);
		messagingActionHandler.sendEmptyMessage(MSG_MESSAGE_ADD_COMPOSING);
		messagingActionHandler.sendEmptyMessage(MSG_SCROLL_TO_BOTTOM);
	}

	private boolean checkAddWhoCardIfRequired() {
		SharedPreferences prefs = ApptentiveInternal.getInstance().getSharedPrefs();
		boolean whoCardDisplayedBefore = wasWhoCardAsPreviouslyDisplayed();
		if (interaction.getWhoCardRequestEnabled() && interaction.getWhoCardRequired()) {
			if (!whoCardDisplayedBefore) {
				addWhoCard(true);
				return true;
			} else {
				String savedEmail = Apptentive.getPersonEmail();
				if (TextUtils.isEmpty(savedEmail)) {
					addWhoCard(false);
					return true;
				}
			}
		}
		return false;
	}

	public void addWhoCard(boolean initial) {
		hideFab();
		hideProfileButton();
		JSONObject profile = interaction.getProfile();
		if (profile != null) {
			pendingWhoCardMode = initial;
			messagingActionHandler.removeMessages(MSG_MESSAGE_ADD_WHOCARD);
			messagingActionHandler.removeMessages(MSG_MESSAGE_ADD_COMPOSING);
			messagingActionHandler.sendEmptyMessage(MSG_REMOVE_STATUS);
			messagingActionHandler.sendMessage(messagingActionHandler.obtainMessage(MSG_MESSAGE_ADD_WHOCARD, initial ? 0 : 1, 0, profile));
			//messagingActionHandler.sendEmptyMessage(MSG_SCROLL_TO_BOTTOM);
		}
	}

	private void addExpectationStatusIfNeeded() {
		messagingActionHandler.sendEmptyMessage(MSG_REMOVE_STATUS);
		messagingActionHandler.sendEmptyMessage(MSG_OPT_INSERT_REGULAR_STATUS);
	}

	public void addNewOutGoingMessageItem(ApptentiveMessage message) {
		messagingActionHandler.sendEmptyMessage(MSG_REMOVE_STATUS);

		messages.add(message);
		messageCenterRecyclerViewAdapter.notifyItemInserted(messages.size() - 1);
		unsentMessagesCount++;
		isPaused = false;
		if (messageCenterRecyclerViewAdapter != null) {
			messageCenterRecyclerViewAdapter.setPaused(isPaused);
		}
	}

	public void displayNewIncomingMessageItem(ApptentiveMessage message) {
		// TODO: Use handler for this
		messagingActionHandler.sendEmptyMessage(MSG_REMOVE_STATUS);
		// TODO: A simpler way to put the message in the correct place.
		// Determine where to insert the new incoming message. It will be in front of any eidting
		// area, i.e. composing, Who Card ...
		int insertIndex = messages.size();

		for (MessageCenterUtil.MessageCenterListItem item : messages) {
			if (item.getListItemType() == MESSAGE_COMPOSER) {
				insertIndex -= 1;
				continue;
			}
			if (item.getListItemType() == MESSAGE_CONTEXT) {
				insertIndex -= 1;
				continue;
			}
			if (item.getListItemType() == WHO_CARD) {
				insertIndex -= 1;
				continue;
			}
		}
		messages.add(insertIndex, message);

		int firstIndex = messageCenterRecyclerView.getFirstVisiblePosition();
		int lastIndex = messageCenterRecyclerView.getLastVisiblePosition();
		boolean composingAreaTakesUpVisibleArea = firstIndex <= insertIndex && insertIndex < lastIndex;
		if (composingAreaTakesUpVisibleArea) {
			View v = messageCenterRecyclerView.getChildAt(0);
			int top = (v == null) ? 0 : v.getTop();
			updateMessageSentStates();
			if (messageCenterRecyclerViewAdapter != null) {
				messageCenterRecyclerViewAdapter.notifyDataSetChanged();
			}
			// Restore the position of listview to composing view
			messagingActionHandler.sendMessage(messagingActionHandler.obtainMessage(MSG_SCROLL_FROM_TOP,
				insertIndex, top));
		} else {
			updateMessageSentStates();
			if (messageCenterRecyclerViewAdapter != null) {
				messageCenterRecyclerViewAdapter.notifyDataSetChanged();
			}
		}

	}


	public void addAttachmentsToComposer(final List<ImageItem> images) {
		ArrayList<ImageItem> newImages = new ArrayList<ImageItem>();
		// only add new images, and filter out duplicates
		if (images != null && images.size() > 0) {
			for (ImageItem newImage : images) {
				boolean bDupFound = false;
				for (ImageItem pendingAttachment : pendingAttachments) {
					if (newImage.originalPath.equals(pendingAttachment.originalPath)) {
						bDupFound = true;
						break;
					}
				}
				if (bDupFound) {
					continue;
				} else {
					pendingAttachments.add(newImage);
					newImages.add(newImage);
				}
			}
		}
		View v = messageCenterRecyclerView.getChildAt(0);
		int top = (v == null) ? 0 : v.getTop();

		if (newImages.isEmpty()) {
			return;
		}
		if (messageCenterRecyclerViewAdapter != null) {
			// Only update composing view if image is attached successfully
			messageCenterRecyclerViewAdapter.addImagestoComposer(composer, newImages);
			//messageCenterRecyclerViewAdapter.notify();
		}
		int firstIndex = messageCenterRecyclerView.getFirstVisiblePosition();
		messagingActionHandler.sendMessage(messagingActionHandler.obtainMessage(MSG_SCROLL_FROM_TOP, firstIndex, top));
	}

	public void setAttachmentsInComposer(final List<ImageItem> images) {
		ApptentiveLog.e("setAttachmentsInComposer()");
		View v = messageCenterRecyclerView.getChildAt(0);
		int top = (v == null) ? 0 : v.getTop();
		// Only update composing view if image is attached successfully
		if (messageCenterRecyclerViewAdapter != null) {
			ApptentiveLog.e("Restoring attachments");
			messageCenterRecyclerViewAdapter.addImagestoComposer(composer, images);
		}
		int firstIndex = messageCenterRecyclerView.getFirstVisiblePosition();
/*
		if (messageCenterRecyclerViewAdapter != null) {
			messageCenterRecyclerViewAdapter.notifyDataSetChanged();
		}
*/
		messagingActionHandler.sendMessage(messagingActionHandler.obtainMessage(MSG_SCROLL_FROM_TOP, firstIndex, top));
	}

	public void removeImageFromComposer(final int position) {
		EngagementModule.engageInternal(hostingActivityRef.get(), interaction, MessageCenterInteraction.EVENT_NAME_ATTACHMENT_DELETE);
		pendingAttachments.remove(position);
		if (messageCenterRecyclerViewAdapter != null) {
			messageCenterRecyclerViewAdapter.removeImageFromComposer(composer, position);
//			int count = imageAttachmentstList.size();
			// Show keyboard if all attachments have been removed
			messageCenterRecyclerViewAdapter.notifyDataSetChanged();
		}
		messagingActionHandler.sendEmptyMessageDelayed(MSG_SCROLL_TO_BOTTOM, DEFAULT_DELAYMILLIS);
	}

	public void openNonImageAttachment(final ImageItem image) {
		if (image == null) {
			ApptentiveLog.d("No attachment argument.");
			return;
		}

		try {
			if (!Util.openFileAttachment(hostingActivityRef.get(), image.originalPath, image.localCachePath, image.mimeType)) {
				ApptentiveLog.d("Cannot open file attachment");
			}
		} catch (Exception e) {
			ApptentiveLog.e("Error loading attachment", e);
		}
	}

	public void showAttachmentDialog(final ImageItem image) {
		if (image == null) {
			ApptentiveLog.d("No attachment argument.");
			return;
		}

		try {

			FragmentTransaction ft = getFragmentManager().beginTransaction();
			Fragment prev = getFragmentManager().findFragmentByTag(DIALOG_IMAGE_PREVIEW);
			if (prev != null) {
				ft.remove(prev);
			}
			ft.addToBackStack(null);

			// Create and show the dialog.
			AttachmentPreviewDialog dialog = AttachmentPreviewDialog.newInstance(image);
			dialog.show(ft, DIALOG_IMAGE_PREVIEW);

		} catch (Exception e) {
			ApptentiveLog.e("Error loading attachment preview.", e);
		}
	}


	@SuppressWarnings("unchecked")
	// We should never get a message passed in that is not appropriate for the view it goes into.
	public synchronized void onMessageSent(ApptentiveHttpResponse response, final ApptentiveMessage apptentiveMessage) {
		if (response.isSuccessful() || response.isRejectedPermanently() || response.isBadPayload()) {
			messagingActionHandler.sendMessage(messagingActionHandler.obtainMessage(MSG_MESSAGE_SENT,
				apptentiveMessage));
		}
	}

	public synchronized void onPauseSending(int reason) {
		messagingActionHandler.sendMessage(messagingActionHandler.obtainMessage(MSG_PAUSE_SENDING,
			reason, 0));
	}

	public synchronized void onResumeSending() {
		messagingActionHandler.sendEmptyMessage(MSG_RESUME_SENDING);
	}

	@Override
	public void onComposingViewCreated(MessageComposerHolder composer, final EditText composerEditText, final ApptentiveImageGridView attachments) {
		ApptentiveLog.e("onComposingViewCreated()");
		EngagementModule.engageInternal(hostingActivityRef.get(), interaction, MessageCenterInteraction.EVENT_NAME_COMPOSE_OPEN);

		this.composer = composer;
		this.composerEditText = composerEditText;

		SharedPreferences prefs = ApptentiveInternal.getInstance().getSharedPrefs();
		// Restore composing text editing state, such as cursor position, after rotation
		if (composingViewSavedState != null) {
			if (this.composerEditText != null) {
				this.composerEditText.onRestoreInstanceState(composingViewSavedState);
			}
			composingViewSavedState = null;
			SharedPreferences.Editor editor = prefs.edit();
			editor.remove(Constants.PREF_KEY_MESSAGE_CENTER_PENDING_COMPOSING_MESSAGE).apply();
		}
		// Restore composing text
		if (prefs.contains(Constants.PREF_KEY_MESSAGE_CENTER_PENDING_COMPOSING_MESSAGE)) {
			String messageText = prefs.getString(Constants.PREF_KEY_MESSAGE_CENTER_PENDING_COMPOSING_MESSAGE, null);
			if (messageText != null && this.composerEditText != null) {
				this.composerEditText.setText(messageText);
			}
			// Stored pending composing text has been restored, remove it from the persistent storage
			SharedPreferences.Editor editor = prefs.edit();
			editor.remove(Constants.PREF_KEY_MESSAGE_CENTER_PENDING_COMPOSING_MESSAGE).apply();
		}

		setAttachmentsInComposer(pendingAttachments);

		messageCenterRecyclerView.setPadding(0, 0, 0, 0);

		if (composerEditText != null) {
			composerEditText.requestFocus();
			if (forceShowKeyboard) {
				composerEditText.post(new Runnable() {
					@Override
					public void run() {
						if (forceShowKeyboard) {
							forceShowKeyboard = false;
							Util.showSoftKeyboard(hostingActivityRef.get(), composerEditText);
						}
					}
				});
			}
		}
		hideFab();
		composer.setSendButtonState();
	}

	@Override
	public void onWhoCardViewCreated(final EditText nameEditText, final EditText emailEditText) {
		this.whoCardNameEditText = nameEditText;
		this.whoCardEmailEditText = emailEditText;
		if (pendingWhoCardName != null) {
			nameEditText.onRestoreInstanceState(pendingWhoCardName);
			//pendingWhoCardName = null;
		}
		if (pendingWhoCardEmail != null) {
			emailEditText.onRestoreInstanceState(pendingWhoCardEmail);
			//pendingWhoCardEmail = null;
		}
		messageCenterRecyclerView.setPadding(0, 0, 0, 0);

		// TODO: Track which field has focus and apply correctly
		if (nameEditText != null) {
			nameEditText.requestFocus();
			if (forceShowKeyboard) {
				nameEditText.post(new Runnable() {
					@Override
					public void run() {
						if (forceShowKeyboard) {
							forceShowKeyboard = false;
							Util.showSoftKeyboard(hostingActivityRef.get(), nameEditText);
						}
					}
				});
			}
		}
		hideFab();
	}

	@Override
	public void beforeComposingTextChanged(CharSequence str) {

	}

	@Override
	public void onComposingTextChanged(CharSequence str) {
	}

	@Override
	public void afterComposingTextChanged(String message) {
		composer.setSendButtonState();
	}

	@Override
	public void onCancelComposing() {
		Util.hideSoftKeyboard(hostingActivityRef.get(), getView());

		JSONObject data = new JSONObject();
		try {
			Editable content = getPendingComposingContent();
			int bodyLength = (content != null) ? content.toString().trim().length() : 0;
			data.put("body_length", bodyLength);
		} catch (JSONException e) {
			//
		}
		EngagementModule.engageInternal(hostingActivityRef.get(), interaction, MessageCenterInteraction.EVENT_NAME_COMPOSE_CLOSE, data.toString());
		messagingActionHandler.sendMessage(messagingActionHandler.obtainMessage(MSG_REMOVE_COMPOSER));
		if (messageCenterRecyclerViewAdapter != null) {
			addExpectationStatusIfNeeded();
		}
		pendingAttachments.clear();
		composerEditText.getText().clear();
		//pendingMessage = null;
		clearPendingComposingMessage();
		showFab();
		showProfileButton();
	}

	@Override
	public void onFinishComposing() {
		messagingActionHandler.sendEmptyMessage(MSG_REMOVE_COMPOSER);

		Util.hideSoftKeyboard(hostingActivityRef.get(), getView());
		if (contextMessage != null) {
			// TODO: Maybe simply send any context messages in the list when a message is sent?
			Message sendContextMessage = messagingActionHandler.obtainMessage(MSG_SEND_CONTEXT_MESSAGE, contextMessage.getBody());
			unsentMessagesCount++;
			messagingActionHandler.sendMessage(sendContextMessage);
			contextMessage = null;
		}
		if (!TextUtils.isEmpty(composerEditText.getText()) || pendingAttachments.size() > 0) {
			Bundle b = new Bundle();
			b.putString(COMPOSING_EDITTEXT_STATE, composerEditText.getText().toString());
			b.putParcelableArrayList(COMPOSING_ATTACHMENTS, new ArrayList<Parcelable>(pendingAttachments));
			Message msg = messagingActionHandler.obtainMessage(MSG_START_SENDING, composerEditText.getText().toString());
			msg.setData(b);
			ApptentiveLog.e("Send Send Message Message");
			unsentMessagesCount++;
			messagingActionHandler.sendMessage(msg);
			//pendingMessage = null;
			composerEditText.getText().clear();
			pendingAttachments.clear();
			clearPendingComposingMessage();
		}
		showFab();
		showProfileButton();
	}

	private void removeItemsFromRecyclerView(int type) {
		for (int i = 0; i < messages.size(); i++) {
			MessageCenterUtil.MessageCenterListItem item = messages.get(i);
			if (item.getListItemType() == type) {
				messages.remove(i);
				messageCenterRecyclerViewAdapter.notifyItemRemoved(i);
			}
		}
	}

	@Override
	public void onSubmitWhoCard(String buttonLabel) {
		ApptentiveLog.e("onSubmitWhoCard()");
		JSONObject data = new JSONObject();
		try {
			data.put("required", interaction.getWhoCardRequired());
			data.put("button_label", buttonLabel);
		} catch (JSONException e) {
			//
		}
		EngagementModule.engageInternal(hostingActivityRef.get(), interaction, MessageCenterInteraction.EVENT_NAME_PROFILE_SUBMIT, data.toString());

		setWhoCardAsPreviouslyDisplayed();
		cleanupWhoCard();

		if (shouldOpenComposerAfterClosingWhoCard()) {
			addComposingCard();
		} else {
			showFab();
			showProfileButton();
		}
	}

	@Override
	public void onCloseWhoCard(String buttonLabel) {
		ApptentiveLog.e("onCloseWhoCard()");
		JSONObject data = new JSONObject();
		try {
			data.put("required", interaction.getWhoCardRequired());
			data.put("button_label", buttonLabel);
		} catch (JSONException e) {
			//
		}
		EngagementModule.engageInternal(hostingActivityRef.get(), interaction, MessageCenterInteraction.EVENT_NAME_PROFILE_CLOSE, data.toString());

		setWhoCardAsPreviouslyDisplayed();
		cleanupWhoCard();

		if (shouldOpenComposerAfterClosingWhoCard()) {
			addComposingCard();
		} else {
			showFab();
			showProfileButton();
		}
	}

	private boolean shouldOpenComposerAfterClosingWhoCard() {
		return interaction.getWhoCard().isRequire() && (recyclerViewContainsItemOfType(MESSAGE_CONTEXT) || recyclerViewContainsItemOfType(MESSAGE_OUTGOING));
	}

	public void cleanupWhoCard() {
		messagingActionHandler.sendEmptyMessage(MSG_MESSAGE_REMOVE_WHOCARD);
		Util.hideSoftKeyboard(hostingActivityRef.get(), getView());
		pendingWhoCardName = null;
		pendingWhoCardEmail = null;
		pendingWhoCardAvatarFile = null;
		pendingWhoCardMode = false;
		whoCardNameEditText = null;
		whoCardEmailEditText = null;
		addExpectationStatusIfNeeded();
	}

	@Override
	public void onNewMessageReceived(final CompoundMessage apptentiveMsg) {
		messagingActionHandler.sendMessage(messagingActionHandler.obtainMessage(MSG_MESSAGE_ADD_INCOMING, apptentiveMsg));
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {

	}

	/* Show header elevation when listview can scroll up; flatten header when listview
	 * scrolls to the top; For pre-llolipop devices, fallback to a divider
	 */
	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		boolean bCanScrollUp;
		if (android.os.Build.VERSION.SDK_INT < 14) {
			bCanScrollUp = view.getChildCount() > 0
				&& (view.getFirstVisiblePosition() > 0 ||
				view.getChildAt(0).getTop() < view.getPaddingTop());
		} else {
			bCanScrollUp = ViewCompat.canScrollVertically(view, -1);
		}
		showToolbarElevation(bCanScrollUp);
	}

	@Override
	public void OnListViewResize(int w, int h, int oldw, int oldh) {
		// detect keyboard launching. If height difference is more than 100 pixels, probably due to keyboard
		if (oldh > h && oldh - h > 100) {
/* TODO: Replace this?
			if (messageCenterRecyclerViewAdapter.getComposerEditText() != null) {
				// When keyboard is up, adjust the scolling such that the cursor is always visible
				final int firstIndex = messageCenterRecyclerView.getFirstVisiblePosition();
				int lastIndex = messageCenterRecyclerView.getLastVisiblePosition();
				View v = messageCenterRecyclerView.getChildAt(lastIndex - firstIndex);
				int top = (v == null) ? 0 : v.getTop();
				if (composerEditText != null) {
					int pos = composerEditText.getSelectionStart();
					Layout layout = composerEditText.getLayout();
					int line = layout.getLineForOffset(pos);
					int baseline = layout.getLineBaseline(line);
					int ascent = layout.getLineAscent(line);
					if (top + baseline - ascent > oldh - h) {
						messagingActionHandler.sendMessageDelayed(messagingActionHandler.obtainMessage(MSG_SCROLL_FROM_TOP,
							lastIndex, top - (oldh - h)), DEFAULT_DELAYMILLIS);
					} else {
						messagingActionHandler.sendMessageDelayed(messagingActionHandler.obtainMessage(MSG_SCROLL_FROM_TOP,
							lastIndex, top), DEFAULT_DELAYMILLIS);
					}
				}
			}
*/
		}
	}

	/* Callback when the attach button is clicked
	 *
	 */
	@Override
	public void onAttachImage() {
		try {
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {//prior Api level 19
				Intent intent = new Intent();
				intent.setType("image/*");
				intent.setAction(Intent.ACTION_GET_CONTENT);
				Intent chooserIntent = Intent.createChooser(intent, null);
				startActivityForResult(chooserIntent, Constants.REQUEST_CODE_PHOTO_FROM_SYSTEM_PICKER);
			} else {
				Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				intent.setType("image/*");
				Intent chooserIntent = Intent.createChooser(intent, null);
				startActivityForResult(chooserIntent, Constants.REQUEST_CODE_PHOTO_FROM_SYSTEM_PICKER);
			}
			imagePickerLaunched = true;
		} catch (Exception e) {
			e.printStackTrace();
			imagePickerLaunched = false;
			ApptentiveLog.d("can't launch image picker");
		}
	}

	private void setWhoCardAsPreviouslyDisplayed() {
		SharedPreferences prefs = ApptentiveInternal.getInstance().getSharedPrefs();
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(Constants.PREF_KEY_MESSAGE_CENTER_WHO_CARD_DISPLAYED_BEFORE, true);
		editor.apply();
	}

	private boolean wasWhoCardAsPreviouslyDisplayed() {
		SharedPreferences prefs = ApptentiveInternal.getInstance().getSharedPrefs();
		return prefs.getBoolean(Constants.PREF_KEY_MESSAGE_CENTER_WHO_CARD_DISPLAYED_BEFORE, false);
	}

	// Retrieve the content from the composing area
	public Editable getPendingComposingContent() {
		return (composerEditText == null) ? null : composerEditText.getText();
	}


	public void savePendingComposingMessage() {
		Editable content = getPendingComposingContent();
		SharedPreferences prefs = ApptentiveInternal.getInstance().getSharedPrefs();
		SharedPreferences.Editor editor = prefs.edit();
		if (content != null) {
			editor.putString(Constants.PREF_KEY_MESSAGE_CENTER_PENDING_COMPOSING_MESSAGE, content.toString().trim());
		} else {
			editor.remove(Constants.PREF_KEY_MESSAGE_CENTER_PENDING_COMPOSING_MESSAGE);
		}

		JSONArray pendingAttachmentsJsonArray = new JSONArray();
		// Save pending attachment
		for (ImageItem pendingAttachment : pendingAttachments) {
			pendingAttachmentsJsonArray.put(pendingAttachment.toJSON());
		}

		if (pendingAttachmentsJsonArray.length() > 0) {
			editor.putString(Constants.PREF_KEY_MESSAGE_CENTER_PENDING_COMPOSING_ATTACHMENTS, pendingAttachmentsJsonArray.toString());
		} else {
			editor.remove(Constants.PREF_KEY_MESSAGE_CENTER_PENDING_COMPOSING_ATTACHMENTS);
		}
		editor.apply();
	}

	/* When no composing view is presented in the list view, calling this method
	 * will clear the pending composing message previously saved in shared preference
	 */
	public void clearPendingComposingMessage() {
		SharedPreferences prefs = ApptentiveInternal.getInstance().getSharedPrefs();
		prefs.edit()
			.remove(Constants.PREF_KEY_MESSAGE_CENTER_PENDING_COMPOSING_MESSAGE)
			.remove(Constants.PREF_KEY_MESSAGE_CENTER_PENDING_COMPOSING_ATTACHMENTS)
			.apply();
	}

	private Parcelable saveEditTextInstanceState() {
		if (composerEditText != null) {
			// Hide keyboard if the keyboard was up prior to rotation
			Util.hideSoftKeyboard(hostingActivityRef.get(), getView());
			return composerEditText.onSaveInstanceState();
		}
		return null;
	}

	Set<String> dateStampsSeen = new HashSet<String>();

	public void updateMessageSentStates() {
		dateStampsSeen.clear();
		MessageCenterUtil.CompoundMessageCommonInterface lastSent = null;
		Set<String> uniqueNonce = new HashSet<String>();
		Iterator<MessageCenterUtil.MessageCenterListItem> messageIterator = messages.iterator();
		while (messageIterator.hasNext()) {
			MessageCenterUtil.MessageCenterListItem message = messageIterator.next();
			if (message instanceof ApptentiveMessage) {
				/* Check if there is any duplicate messages and remove if found.
				* add() of a Set returns false if the element already exists.
				 */
				if (!uniqueNonce.add(((ApptentiveMessage) message).getNonce())) {
					messageIterator.remove();
					continue;
				}
				// Update timestamps
				ApptentiveMessage apptentiveMessage = (ApptentiveMessage) message;
				Double sentOrReceivedAt = apptentiveMessage.getCreatedAt();
				String dateStamp = createDatestamp(sentOrReceivedAt);
				if (dateStamp != null) {
					if (dateStampsSeen.add(dateStamp)) {
						apptentiveMessage.setDatestamp(dateStamp);
					} else {
						apptentiveMessage.clearDatestamp();
					}
				}

				//Find last sent
				if (apptentiveMessage.isOutgoingMessage()) {
					if (sentOrReceivedAt != null && sentOrReceivedAt > Double.MIN_VALUE) {
						lastSent = (MessageCenterUtil.CompoundMessageCommonInterface) apptentiveMessage;
						lastSent.setLastSent(false);
					}

				}
			}
		}

		if (lastSent != null) {
			lastSent.setLastSent(true);
		}
	}

	protected String createDatestamp(Double seconds) {
		if (seconds != null && seconds > Double.MIN_VALUE) {
			Date date = new Date(Math.round(seconds * 1000));
			DateFormat mediumDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
			return mediumDateFormat.format(date);
		}
		return null;
	}

	private int calculateFabPadding(Context context) {
		Resources res = context.getResources();
		float scale = res.getDisplayMetrics().density;
		return (int) (res.getDimension(R.dimen.apptentive_message_center_bottom_padding) * scale + 0.5f);

	}

	private void showFab() {
		ApptentiveLog.e("showFab()");
		messageCenterRecyclerView.setPadding(0, 0, 0, fabPaddingPixels);
		// Re-enable Fab at the beginning of the animation
		if (fab.getVisibility() != View.VISIBLE) {
			fab.setEnabled(true);
			AnimationUtil.scaleFadeIn(fab);
		}
	}

	private void hideFab() {
		ApptentiveLog.e("hideFab()");
		// Make sure Fab is not clickable during fade-out animation
		if (fab.getVisibility() != View.GONE) {
			fab.setEnabled(false);
			AnimationUtil.scaleFadeOutGone(fab);
		}
	}

	private void showProfileButton() {
		bShowProfileMenuItem = true;
		updateMenuVisibility();
	}

	private void hideProfileButton() {
		bShowProfileMenuItem = false;
		updateMenuVisibility();
	}

	/*
	 * Messages returned from the database was sorted on KEY_ID, which was generated by server
	 * with seconds resolution. If messages were received by server within a second, messages may be out of order
	 * This method uses insertion sort to re-sort the messages retrieved from the database
	 */
	private void prepareMessages(final List<MessageCenterUtil.MessageCenterListItem> originalItems) {
		messages.clear();
		unsentMessagesCount = 0;
		// Loop through each message item retrieved from database
		for (MessageCenterUtil.MessageCenterListItem item : originalItems) {
			if (item instanceof ApptentiveMessage) {
				ApptentiveMessage apptentiveMessage = (ApptentiveMessage) item;
				Double createdAt = apptentiveMessage.getCreatedAt();
				if (apptentiveMessage.isOutgoingMessage() && createdAt == null) {
					unsentMessagesCount++;
				}

				/*
				 * Find proper location to insert into the messages list of the listview.
				 */
				ListIterator<MessageCenterUtil.MessageCenterListItem> listIterator = messages.listIterator();
				ApptentiveMessage next = null;
				while (listIterator.hasNext()) {
					next = (ApptentiveMessage) listIterator.next();
					Double nextCreatedAt = next.getCreatedAt();
					// For unsent and dropped message, move the iterator to the end, and append there
					if (createdAt == null || createdAt <= Double.MIN_VALUE) {
						continue;
					}
					// next message has not received by server or received, but has a later created_at time
					if (nextCreatedAt == null || nextCreatedAt > createdAt) {
						break;
					}
				}

				if (next == null || next.getCreatedAt() == null || createdAt == null || next.getCreatedAt() <= createdAt ||
					createdAt <= Double.MIN_VALUE) {
					listIterator.add(item);
				} else {
					// Add in front of the message that has later created_at time
					listIterator.set(item);
					listIterator.add(next);
				}
			}
		}
		messagingActionHandler.sendEmptyMessage(MSG_ADD_GREETING);
	}

	//	@Override
	public void onClickAttachment(final int position, final ImageItem image) {
		if (Util.isMimeTypeImage(image.mimeType)) {
			// "+" placeholder is clicked
			if (TextUtils.isEmpty(image.originalPath)) {
				onAttachImage();
			} else {
				// an image thumbnail is clicked
				showAttachmentDialog(image);
			}
		} else {
			// a generic attachment icon is clicked
			openNonImageAttachment(image);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == Constants.REQUEST_READ_STORAGE_PERMISSION) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				onAttachImage();
			}
		}
	}


	/*
	 * Called when attachment overlaid "selection" ui is tapped. The "selection" ui could be selection checkbox
	 * or close button
	 */
	@Override
	public void onImageSelected(int index) {
		removeImageFromComposer(index);
	}

	@Override
	public void onImageUnselected(String path) {

	}

	@Override
	public void onCameraShot(File imageFile) {

	}


	private static class MessagingActionHandler extends Handler {

		private final WeakReference messageCenterFragmentWeakReference;

		public MessagingActionHandler(MessageCenterFragment fragment) {
			messageCenterFragmentWeakReference = new WeakReference(fragment);
		}

		public void handleMessage(Message msg) {
			MessageCenterFragment fragment = (MessageCenterFragment) messageCenterFragmentWeakReference.get();
			/* Message can be delayed. If so, make sure fragment is still available and attached to activity
			 * messageCenterRecyclerViewAdapter will always be set null in onDetach(). it's a good indication if
			 * fragment is attached.
			 */
			if (fragment == null || fragment.messageCenterRecyclerViewAdapter == null) {
				return;
			}
			switch (msg.what) {
				case MSG_MESSAGE_ADD_WHOCARD: {
					ApptentiveLog.e("Adding Who Card");
					// msg.arg1 is either WHO_CARD_MODE_INIT or WHO_CARD_MODE_EDIT
					boolean initial = msg.arg1 == 0; // TODO: Do something with mode?
					WhoCard whoCard = fragment.interaction.getWhoCard();
					whoCard.setInitial(initial);
					fragment.messages.add(whoCard);
					fragment.messageCenterRecyclerViewAdapter.notifyItemInserted(fragment.messages.size() - 1);
					fragment.messageCenterRecyclerView.setSelection(fragment.messages.size() - 1);
					break;
				}
				case MSG_MESSAGE_REMOVE_WHOCARD: {
					List<MessageCenterUtil.MessageCenterListItem> messages = fragment.messages;
					ListIterator<MessageCenterUtil.MessageCenterListItem> messageIterator = messages.listIterator();
					while (messageIterator.hasNext()) {
						int i = messageIterator.nextIndex();
						MessageCenterUtil.MessageCenterListItem next = messageIterator.next();
						if (next.getListItemType() == WHO_CARD) {
							ApptentiveLog.e("Removing Who Card");
							messageIterator.remove();
							fragment.messageCenterRecyclerViewAdapter.notifyItemRemoved(i);
						}
					}
					break;
				}
				case MSG_MESSAGE_ADD_COMPOSING: {
					fragment.messages.add(fragment.interaction.getComposer());
					fragment.messageCenterRecyclerViewAdapter.notifyItemInserted(fragment.messages.size() - 1);
					fragment.messageCenterRecyclerView.setSelection(fragment.messages.size() - 1);
					break;
				}
				case MSG_MESSAGE_ADD_INCOMING: {
					ApptentiveMessage apptentiveMessage = (ApptentiveMessage) msg.obj;
					fragment.displayNewIncomingMessageItem(apptentiveMessage);
					break;
				}
				case MSG_SCROLL_TO_BOTTOM: {
					fragment.messageCenterRecyclerView.setSelection(fragment.messages.size() - 1);
					fragment.messageCenterRecyclerView.scrollToPosition(fragment.messages.size() - 1);
					break;
				}
				case MSG_SCROLL_FROM_TOP: {
					int index = msg.arg1;
					int top = msg.arg2;
					fragment.messageCenterRecyclerView.setSelectionFromTop(index, top);
					break;
				}
				case MSG_MESSAGE_SENT: {
					// below is callback handling when receiving of message is acknowledged by server through POST response
					fragment.unsentMessagesCount--;
					ApptentiveMessage apptentiveMessage = (ApptentiveMessage) msg.obj;
					for (MessageCenterUtil.MessageCenterListItem message : fragment.messages) {
						if (message instanceof ApptentiveMessage) {
							String nonce = ((ApptentiveMessage) message).getNonce();
							if (nonce != null) {
								String sentNonce = apptentiveMessage.getNonce();
								if (sentNonce != null && nonce.equals(sentNonce)) {
									((ApptentiveMessage) message).setCreatedAt(apptentiveMessage.getCreatedAt());
									break;
								}
							}
						}
					}
					//Update timestamp display and add status message if needed
					fragment.updateMessageSentStates();
					fragment.addExpectationStatusIfNeeded();

					// Calculate the listview offset to make sure updating sent timestamp does not push the current view port
					int firstIndex = fragment.messageCenterRecyclerView.getFirstVisiblePosition();
					View v = fragment.messageCenterRecyclerView.getChildAt(0);
					int top = (v == null) ? 0 : v.getTop();

					fragment.messageCenterRecyclerViewAdapter.notifyDataSetChanged();
					// If Who Card is being shown while a message is sent, make sure Who Card is still in view by scrolling to bottom
					if (fragment.recyclerViewContainsItemOfType(WHO_CARD)) {
						sendEmptyMessageDelayed(MSG_SCROLL_TO_BOTTOM, DEFAULT_DELAYMILLIS);
					} else {
						sendMessageDelayed(obtainMessage(MSG_SCROLL_FROM_TOP, firstIndex, top), DEFAULT_DELAYMILLIS);
					}
					break;
				}
				case MSG_START_SENDING: {
					Bundle b = msg.getData();
					CompoundMessage message = new CompoundMessage();
					message.setBody(b.getString(COMPOSING_EDITTEXT_STATE));
					message.setRead(true);
					message.setCustomData(ApptentiveInternal.getInstance().getAndClearCustomData());
					ArrayList<ImageItem> imagesToAttach = b.getParcelableArrayList(COMPOSING_ATTACHMENTS);
					message.setAssociatedImages(imagesToAttach);

					ApptentiveLog.e("Adding Message to list");
					fragment.messages.add(message);

					ApptentiveLog.e("Sending message");
					ApptentiveInternal.getInstance().getMessageManager().sendMessage(message);

					// TODO: Move this somewhere else?
					// After the message is sent, check if Who Card need to be shown for the 1st time(When Who Card is either requested or required)
					SharedPreferences prefs = ApptentiveInternal.getInstance().getSharedPrefs();
					boolean whoCardDisplayedBefore = fragment.wasWhoCardAsPreviouslyDisplayed();
					if (!whoCardDisplayedBefore) {
						JSONObject data = new JSONObject();
						try {
							data.put("required", fragment.interaction.getWhoCardRequired());
							data.put("trigger", "automatic");
						} catch (JSONException e) {
							//
						}
						EngagementModule.engageInternal(fragment.hostingActivityRef.get(), fragment.interaction, MessageCenterInteraction.EVENT_NAME_PROFILE_OPEN, data.toString());
						// The delay is to ensure the animation of adding Who Card play after the animation of new outgoing message
						if (fragment.interaction.getWhoCardRequestEnabled()) {
							fragment.forceShowKeyboard = true;
							fragment.addWhoCard(true);
						}
					}
					break;
				}
				case MSG_SEND_CONTEXT_MESSAGE: {
					List<MessageCenterUtil.MessageCenterListItem> messages = fragment.messages;
					// Remove fake ContextMessage from the RecyclerView
					for (int i = 0; i < messages.size(); i++) {
						MessageCenterUtil.MessageCenterListItem item = messages.get(i);
						if (item.getListItemType() == MESSAGE_CONTEXT) {
							ApptentiveLog.e("Removing Fake Context Message");
							messages.remove(i);
							fragment.messageCenterRecyclerViewAdapter.notifyItemRemoved(i);
						}
					}
					// Create a CompoundMessage for sending and final display
					String body = (String) msg.obj;
					CompoundMessage message = new CompoundMessage();
					message.setBody(body);
					message.setAutomated(true);
					message.setRead(true);

					// Add it to the RecyclerView
					ApptentiveLog.e("Adding Real Context Message");
					messages.add(message);
					fragment.messageCenterRecyclerViewAdapter.notifyItemInserted(messages.size() - 1);

					// Send it to the server
					ApptentiveLog.e("Sending Real Context Message");
					ApptentiveInternal.getInstance().getMessageManager().sendMessage(message);
					break;
				}
				case MSG_PAUSE_SENDING: {
					if (!fragment.isPaused) {
						fragment.isPaused = true;
						if (fragment.unsentMessagesCount > 0) {
							fragment.messageCenterRecyclerViewAdapter.setPaused(fragment.isPaused);
							int reason = msg.arg1;
							if (reason == MessageManager.SEND_PAUSE_REASON_NETWORK) {
								EngagementModule.engageInternal(fragment.hostingActivityRef.get(), fragment.interaction, MessageCenterInteraction.EVENT_NAME_MESSAGE_NETWORK_ERROR);
								MessageCenterStatus newItem = fragment.interaction.getErrorStatusNetwork();
								// TODO: fragment.addNewStatusItem(newItem);
							} else if (reason == MessageManager.SEND_PAUSE_REASON_SERVER) {
								EngagementModule.engageInternal(fragment.hostingActivityRef.get(), fragment.interaction, MessageCenterInteraction.EVENT_NAME_MESSAGE_HTTP_ERROR);
								MessageCenterStatus newItem = fragment.interaction.getErrorStatusServer();
								// TODO: fragment.addNewStatusItem(newItem);
							}
							fragment.messageCenterRecyclerViewAdapter.notifyDataSetChanged();
						}
					}
					break;
				}
				case MSG_RESUME_SENDING: {
					if (fragment.isPaused) {
						fragment.isPaused = false;
						if (fragment.unsentMessagesCount > 0) {
							// TODO: fragment.clearStatusItem();
						}

						fragment.messageCenterRecyclerViewAdapter.setPaused(fragment.isPaused);
						fragment.messageCenterRecyclerViewAdapter.notifyDataSetChanged();
					}
					break;
				}
				case MSG_REMOVE_COMPOSER: {
					List<MessageCenterUtil.MessageCenterListItem> messages = fragment.messages;
					for (int i = 0; i < messages.size(); i++) {
						MessageCenterUtil.MessageCenterListItem item = messages.get(i);
						if (item.getListItemType() == MESSAGE_COMPOSER) {
							ApptentiveLog.e("Removing Composer");
							messages.remove(i);
							fragment.messageCenterRecyclerViewAdapter.notifyItemRemoved(i);
						}
					}
					break;
				}
				case MSG_OPT_INSERT_REGULAR_STATUS: {
					List<MessageCenterUtil.MessageCenterListItem> messages = fragment.messages;
					MessageCenterStatus status = fragment.interaction.getRegularStatus();
					for (int i = 0; i < messages.size(); i++) {
						MessageCenterUtil.MessageCenterListItem item = messages.get(i);
						if (item.getListItemType() == MESSAGE_COMPOSER) {
							ApptentiveLog.e("Removing Composer");
							messages.remove(i);
							fragment.messageCenterRecyclerViewAdapter.notifyItemRemoved(i);
						}
					}

					int numOfMessages = messages.size();
					if (numOfMessages > 0) {
						// Check if the last message in the view is a sent message
						MessageCenterUtil.MessageCenterListItem lastItem = messages.get(messages.size() - 1);
						if (lastItem != null && lastItem.getListItemType() == MESSAGE_OUTGOING) {
							ApptentiveMessage apptentiveMessage = (ApptentiveMessage) lastItem;
							if (apptentiveMessage.isOutgoingMessage()) {
								Double createdTime = apptentiveMessage.getCreatedAt();
								if (createdTime != null && createdTime > Double.MIN_VALUE) {
									if (status != null) {
										// Add expectation status message if the last is a sent
										messages.add(status);
										fragment.messageCenterRecyclerViewAdapter.notifyItemInserted(messages.size() - 1);
									}
								}
							}
						}
					}

					break;
				}
				case MSG_REMOVE_STATUS: {
					List<MessageCenterUtil.MessageCenterListItem> messages = fragment.messages;
					for (int i = 0; i < messages.size(); i++) {
						MessageCenterUtil.MessageCenterListItem item = messages.get(i);
						if (item.getListItemType() == STATUS) {
							ApptentiveLog.e("Removing Status");
							messages.remove(i);
							fragment.messageCenterRecyclerViewAdapter.notifyItemRemoved(i);
						}
					}
					break;
				}
				case MSG_ADD_CONTEXT_MESSAGE: {
					ApptentiveLog.e("Adding Context Message");
					List<MessageCenterUtil.MessageCenterListItem> messages = fragment.messages;
					String body = (String) msg.obj;
					ContextMessage contextMessage = new ContextMessage(body);
					messages.add(contextMessage);
					fragment.messageCenterRecyclerViewAdapter.notifyItemInserted(messages.size() - 1);
					break;
				}
				case MSG_ADD_GREETING: {
					fragment.messages.add(0, fragment.interaction.getGreeting());
					fragment.messageCenterRecyclerViewAdapter.notifyItemInserted(0);
					break;
				}
			}
		}
	}

	public boolean recyclerViewContainsItemOfType(int type) {
		for (MessageCenterUtil.MessageCenterListItem item : messages) {
			if (item.getListItemType() == type) {
				return true;
			}
		}
		return false;
	}
}