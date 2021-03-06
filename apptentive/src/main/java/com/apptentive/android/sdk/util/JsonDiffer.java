/*
 * Copyright (c) 2014, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.util;

import com.apptentive.android.sdk.ApptentiveLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

import static com.apptentive.android.sdk.ApptentiveLogTag.UTIL;
import static com.apptentive.android.sdk.debug.ErrorMetrics.logException;

/**
 * @author Sky Kelsey
 */
public class JsonDiffer {

	private JsonDiffer() {
	}

	public static JSONObject getDiff(JSONObject original, JSONObject updated) {
		JSONObject ret = new JSONObject();

		Set<String> originalKeys = getKeys(original);
		Set<String> updatedKeys = getKeys(updated);

		Iterator<String> it = originalKeys.iterator();
		while (it.hasNext()) {
			String key = it.next();
			updatedKeys.remove(key);
			try {
				Object oldValue = original.opt(key);
				Object newValue = updated.opt(key);

				if (isEmpty(oldValue)) {
					if (!isEmpty(newValue)) {
						// Old is empty. New is not. Update.
						ret.put(key, newValue);
					}
				} else if (isEmpty(newValue)) {
					// Old is not empty, but new is empty. Clear value.
					ret.put(key, JSONObject.NULL);
				} else if (oldValue instanceof JSONObject && newValue instanceof JSONObject) {
					// Diff JSONObjects
					if (!areObjectsEqual(oldValue, newValue)) {
						ret.put(key, newValue);
					}
				} else if (oldValue instanceof JSONArray && newValue instanceof JSONArray) {
					// Diff JSONArrays
					// TODO: At least check for strict equality. Right now, we always send nested JSONArrays.
					ret.put(key, newValue);
				} else if (!oldValue.equals(newValue)) {
					// Diff primitives
					ret.put(key, newValue);
				} else if (oldValue.equals(newValue)) {
					// Do nothing.
				}
			} catch (JSONException e) {
				ApptentiveLog.w(UTIL, e, "Error diffing object with key %s", key);
				logException(e);
			} finally {
				it.remove();
			}
		}

		// Finally, add in the keys that were added in the new object.
		it = updatedKeys.iterator();
		while (it.hasNext()) {
			String key = it.next();
			try {
				ret.put(key, updated.get(key));
			} catch (JSONException e) {
				logException(e);
			}
		}

		// If there is no difference, return null.
		if (ret.length() == 0) {
			ret = null;
		}
		ApptentiveLog.v(UTIL, "Generated diff: %s", ret);
		return ret;
	}


	public static boolean areObjectsEqual(Object left, Object right) {
		if (left == right) return true;
		if (left == null || right == null) return false;

		if (left instanceof JSONObject && right instanceof JSONObject) {
			JSONObject leftJSONObject = (JSONObject) left;
			JSONObject rightJSONObject = (JSONObject) right;
			if (leftJSONObject.length() != rightJSONObject.length()) {
				return false;
			}
			Iterator keys = leftJSONObject.keys();
			while (keys.hasNext()) {
				try {
					String key = (String) keys.next();
					Object leftValue = leftJSONObject.get(key);
					Object rightValue = rightJSONObject.get(key);
					if (!areObjectsEqual(leftValue, rightValue)) {
						return false;
					}
				} catch (JSONException e) {
					ApptentiveLog.w(UTIL, e, "Error comparing JSONObjects");
					logException(e);
					return false;
				}
			}
			return true;
		} else if (left instanceof JSONArray && right instanceof JSONArray) {
			JSONArray leftArray = (JSONArray) left;
			JSONArray rightArray = (JSONArray) right;
			if (leftArray.length() != rightArray.length()) {
				return false;
			}
			try {
				for (int i = 0; i < leftArray.length(); i++) {
					if (!areObjectsEqual(leftArray.get(i), rightArray.get(i))) {
						return false;
					}
				}
			} catch (JSONException e) {
				ApptentiveLog.e(e, "");
				logException(e);
				return false;
			}
			return true;
		} else if (left instanceof Number && right instanceof Number) {
			// Treat all numbers as doubles. Numbers are equal if within 1/10,000 of each other. This
			// is to account for floating point errors when comparing a float and double of equal value.
			double leftDouble = ((Number) left).doubleValue();
			double rightDouble = ((Number) right).doubleValue();
			double error = Math.abs(0.000001 * rightDouble);
			// Use <= because if both sides are zero, the error will also be zero.
			return Math.abs(leftDouble - rightDouble) <= error;
		} else {
			return left.equals(right);
		}
	}

	private static Set<String> getKeys(JSONObject jsonObject) {
		Set<String> keys = new HashSet<String>();
		if (jsonObject != null) {
			Iterator<String> it = jsonObject.keys();
			while (it.hasNext()) {
				keys.add(it.next());
			}
		}
		return keys;
	}

	private static boolean isEmpty(Object value) {
		return value == null || value.equals("");
	}
}
