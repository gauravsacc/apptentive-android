/*
 * Copyright (c) 2017, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.model;

import com.apptentive.android.sdk.network.HttpRequestMethod;
import com.apptentive.android.sdk.util.StringUtils;

import org.json.JSONException;

public class LogoutPayload extends JsonPayload {
	public LogoutPayload() {
		super(PayloadType.logout);
	}

	public LogoutPayload(String json) throws JSONException {
		super(PayloadType.logout, json);
	}

	//region Http-request

	@Override
	public String getHttpEndPoint(String conversationId) {
		return StringUtils.format("/conversations/%s/session", conversationId);
	}

	@Override
	public HttpRequestMethod getHttpRequestMethod() {
		return HttpRequestMethod.DELETE;
	}

	//endregion
}
