/*
 * Copyright (c) 2017, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.module.messagecenter.model;

import com.apptentive.android.sdk.ApptentiveLog;
import com.apptentive.android.sdk.model.ApptentiveMessage;
import com.apptentive.android.sdk.model.CompoundMessage;
import com.apptentive.android.sdk.util.StringUtils;

import org.json.JSONException;
import org.json.JSONObject;

public class MessageFactory {
	/**
	 * Only use this method if you don't need to know whether the resulting Message is outgoing or
	 * incoming. Use {@link #fromJson(String, String)} otherwise.
	 */
	public static ApptentiveMessage fromJson(String json) {
		return fromJson(json, null);
	}

	public static ApptentiveMessage fromJson(String json, String personId) {
		try {
			// If KEY_TYPE is set to CompoundMessage or not set, treat them as CompoundMessage
			ApptentiveMessage.Type type = ApptentiveMessage.Type.CompoundMessage;
			JSONObject root = new JSONObject(json);
			if (!root.isNull(ApptentiveMessage.KEY_TYPE)) {
				String typeStr = root.getString(ApptentiveMessage.KEY_TYPE);
				if (!StringUtils.isNullOrEmpty(typeStr)) {
					type = ApptentiveMessage.Type.valueOf(typeStr);
				}
			}
			switch (type) {
				case CompoundMessage:
					String senderId = null;
					try {
						if (!root.isNull(ApptentiveMessage.KEY_SENDER)) {
							JSONObject sender = root.getJSONObject(ApptentiveMessage.KEY_SENDER);
							if (!sender.isNull((ApptentiveMessage.KEY_SENDER_ID))) {
								senderId = sender.getString(ApptentiveMessage.KEY_SENDER_ID);
							}
						}
					} catch (JSONException e) {
						// Ignore, senderId would be null
					}
					// If senderId is null or same as the locally stored id, construct message as outgoing
					return new CompoundMessage(json, (senderId == null || (personId != null && senderId.equals(personId))));
				case unknown:
					break;
				default:
					break;
			}
		} catch (JSONException e) {
			ApptentiveLog.v("Error parsing json as Message: %s", e, json);
		} catch (IllegalArgumentException e) {
			// Exception treated as unknown type
		}
		return null;
	}
}
