/*
 * Copyright (c) 2015, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.module.messagecenter.view;

import android.content.Context;
import android.text.Editable;

import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;


import com.apptentive.android.sdk.R;
import com.apptentive.android.sdk.module.messagecenter.model.MessageCenterComposingItem;
import com.apptentive.android.sdk.util.image.ApptentiveImageGridView;
import com.apptentive.android.sdk.util.image.ImageItem;

import java.util.ArrayList;
import java.util.List;


/**
 * @author Barry Li
 */
public class MessageCenterComposingView extends FrameLayout implements MessageCenterListItemView {

	private EditText et;
	// Image Band
	private ApptentiveImageGridView imageBandView;
	List<ImageItem> images = new ArrayList<ImageItem>();

	public MessageCenterComposingView(Context activityContext, final MessageCenterComposingItem item, final MessageAdapter.OnListviewItemActionListener listener) {
		super(activityContext);

		LayoutInflater inflater = LayoutInflater.from(activityContext);
		View parentView = inflater.inflate(R.layout.apptentive_message_center_composing_area, this);
		et = (EditText) parentView.findViewById(R.id.composing_et);
		if (item.str_2 != null) {
			et.setHint(item.str_2);
		}
		et.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
				listener.beforeComposingTextChanged(charSequence);
			}

			@Override
			public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
				listener.onComposingTextChanged(charSequence);
			}

			@Override
			public void afterTextChanged(Editable editable) {
				listener.afterComposingTextChanged(editable.toString());
			}
		});


		imageBandView = (ApptentiveImageGridView) parentView.findViewById(R.id.grid);
		imageBandView.setupUi();
		imageBandView.setupLayoutListener();
		imageBandView.setListener(new ApptentiveImageGridView.ImageItemClickedListener() {
			@Override
			public void onClick(int position, ImageItem image) {
				listener.onShowImagePreView(position, image, true);
			}
		});
		imageBandView.setAdapterIndicator(R.drawable.apptentive_ic_close);
		// Initialize image attachments band with empty data
		clearImageAttachmentBand();
	}

	public EditText getEditText() {
		return et;
	}

	/**
	 * Remove all images from attchment band.
	 */
	public void clearImageAttachmentBand() {
		imageBandView.setVisibility(View.GONE);
		images.clear();

		imageBandView.setData(images);
	}

	/**
	 * Add new images to attchment band.
	 *
	 * @param imagesToAttach an array of new images to add
	 */
	public void addImagesToImageAttachmentBand(final List<ImageItem> imagesToAttach) {

		if (imagesToAttach == null || imagesToAttach.size() == 0) {
			return;
		}
		imageBandView.setupLayoutListener();
		imageBandView.setVisibility(View.VISIBLE);

		images.addAll(imagesToAttach);
		imageBandView.setData(images);

	}

	/**
	 * Remove an image from attchment band.
	 *
	 * @param position the postion index of the image to be removed
	 */
	public void removeImageFromImageAttachmentBand(final int position) {
		images.remove(position);
		imageBandView.setupLayoutListener();
		if (images.size() == 0) {
			// Hide attachment band after last attachment is removed
			imageBandView.setVisibility(View.GONE);
			return;
		}
		imageBandView.setData(images);
	}
}