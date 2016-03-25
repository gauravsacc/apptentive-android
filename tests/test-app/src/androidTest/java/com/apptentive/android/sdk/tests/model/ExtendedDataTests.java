/*
 * Copyright (c) 2014, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.tests.model;

import com.apptentive.android.sdk.model.CommerceExtendedData;
import com.apptentive.android.sdk.model.LocationExtendedData;
import com.apptentive.android.sdk.model.TimeExtendedData;
import com.apptentive.android.sdk.tests.ApptentiveInstrumentationTestCase;
import com.apptentive.android.sdk.ApptentiveLog;
import com.apptentive.android.sdk.tests.util.FileUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Date;

/**
 * @author Sky Kelsey
 */
public class ExtendedDataTests extends ApptentiveInstrumentationTestCase {

	private static final String TEST_DATA_DIR = "model" + File.separator;

	public void testCommerceExtendedData() {
		ApptentiveLog.e("testCommerceExtendedData()");
		try {
			JSONObject expected = new CommerceExtendedData(FileUtil.loadTextAssetAsString(getInstrumentation().getContext(), TEST_DATA_DIR + "testCommerceExtendedData.json"));

			CommerceExtendedData actual = new CommerceExtendedData()
				.setId("commerce_id")
				.setAffiliation(1111111111)
				.setRevenue(100d)
				.setShipping(5l)
				.setTax(4.38f)
				.setCurrency("USD");
			CommerceExtendedData.Item item = new CommerceExtendedData.Item(22222222, "Item Name", "Category", 20, 5.0d, "USD");
			actual.addItem(item);

			assertEquals(expected.toString(), actual.toString());
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	public void testLocationExtendedData() {
		ApptentiveLog.e("testLocationExtendedData()");
		try {
			JSONObject expected = new LocationExtendedData(FileUtil.loadTextAssetAsString(getInstrumentation().getContext(), TEST_DATA_DIR + "testLocationExtendedData.json"));

			LocationExtendedData actual = new LocationExtendedData(-122.34569190000002d, 47.6288591d);

			assertEquals(expected.toString(), actual.toString());
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	public void testTimeExtendedData() {
		ApptentiveLog.e("testTimeExtendedData()");
		try {
			JSONObject expected = new TimeExtendedData(FileUtil.loadTextAssetAsString(getInstrumentation().getContext(), TEST_DATA_DIR + "testTimeExtendedData.json"));

			TimeExtendedData millis = new TimeExtendedData(1406251926165l);
			assertEquals(expected.toString(), millis.toString());

			TimeExtendedData seconds = new TimeExtendedData(1406251926.165);
			assertEquals(expected.toString(), seconds.toString());

			TimeExtendedData date = new TimeExtendedData(new Date(1406251926165l));
			assertEquals(expected.toString(), date.toString());
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}
}
