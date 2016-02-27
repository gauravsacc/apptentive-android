/*
 * Copyright (c) 2016, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.module.engagement.interaction.view.survey;

import android.app.Activity;
import android.content.Context;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.apptentive.android.sdk.R;
import com.apptentive.android.sdk.module.engagement.interaction.model.survey.AnswerDefinition;
import com.apptentive.android.sdk.module.engagement.interaction.model.survey.MultichoiceQuestion;
import com.apptentive.android.sdk.util.Util;

import java.util.*;


public class MultichoiceSurveyQuestionView extends BaseSurveyQuestionView<MultichoiceQuestion> implements RadioGroup.OnCheckedChangeListener {

	RadioGroup radioGroup;
	boolean buttonChecked;

	public MultichoiceSurveyQuestionView(Context context, MultichoiceQuestion question) {
		super(context, question);

		List<AnswerDefinition> answerDefinitions = question.getAnswerChoices();

		inflater.inflate(R.layout.apptentive_survey_question_multichoice, getAnswerContainer());
		radioGroup = (RadioGroup) findViewById(R.id.radio_group);
		radioGroup.setOnCheckedChangeListener(this);

		for (int i = 0; i < answerDefinitions.size(); i++) {
			AnswerDefinition answerDefinition = answerDefinitions.get(i);
			RadioButton choice = new RadioButton(contextThemeWrapper);
			choice.setLayoutParams(new RadioGroup.LayoutParams(LayoutParams.MATCH_PARENT, (int) Util.dipsToPixels(contextThemeWrapper, 48)));
			choice.setText(answerDefinition.getValue());
			choice.setTag(R.id.apptentive_survey_answer_id, answerDefinition.getId());
			radioGroup.addView(choice);
		}
	}

	@Override
	public void onCheckedChanged(RadioGroup group, int checkedId) {
		buttonChecked = true;
		if (getContext() instanceof Activity) {
			Util.hideSoftKeyboard(getContext(), MultichoiceSurveyQuestionView.this);
		}
		fireListener();
	}

	@Override
	public boolean isValid() {
		return !question.isRequired() || buttonChecked;
	}

	@Override
	public Object getAnswer() {
		for (int i = 0; i < radioGroup.getChildCount(); i++) {
			RadioButton radioButton = (RadioButton) radioGroup.getChildAt(i);
			if (radioButton.isChecked()) {
				return radioButton.getTag(R.id.apptentive_survey_answer_id);
			}
		}
		return null;
	}
}
