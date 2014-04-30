/**
 * Copyright (C) 2014 James Jory (james@vintank.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.plugin.slack.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Attachment {
	private String fallback;
	private String text;
	private String pretext;
	private String color;
	private List<Field> fields;
	
	Attachment() {
	}
	
	public Attachment(String fallback) {
		this.fallback = fallback;
	}

	public static Attachment instance(String fallback) {
		return new Attachment(fallback);
	}
	
	public Attachment fallback(String fallback) {
		setFallback(fallback);
		return this;
	}
	
	public Attachment text(String text) {
		setText(text);
		return this;
	}
	
	public Attachment pretext(String pretext) {
		setPretext(pretext);
		return this;
	}
	
	public Attachment color(String color) {
		setColor(color);
		return this;
	}
	
	public Attachment fields(Field...fields) {
		if (this.fields == null)
			this.fields = new ArrayList<Field>(Arrays.asList(fields));
		else
			this.fields.addAll(Arrays.asList(fields));
		return this;
	}
	
	public String getFallback() {
		return fallback;
	}

	public void setFallback(String fallback) {
		this.fallback = fallback;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getPretext() {
		return pretext;
	}

	public void setPretext(String pretext) {
		this.pretext = pretext;
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public List<Field> getFields() {
		return fields;
	}

	public void addField(Field field) {
		if (fields == null)
			fields = new ArrayList<Field>(3);
		fields.add(field);
	}
	
	public void setFields(List<Field> fields) {
		this.fields = fields;
	}
}
