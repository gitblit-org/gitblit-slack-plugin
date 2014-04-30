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

import com.google.gson.annotations.SerializedName;

public class Field {
	private String title;
	private String value;
	@SerializedName("short")
	private boolean isShort;

	Field() {
	}

	public Field(String title) {
		this(title, null, false);
	}

	public Field(String title, String value) {
		this(title, value, false);
	}

	public Field(String title, String value, boolean isShort) {
		this.title = title;
		this.value = value;
		this.isShort = isShort;
	}

	public static Field instance(String title) {
		return new Field(title);
	}

	public static Field instance(String title, String value) {
		return new Field(title, value);
	}

	public Field title(String title) {
		setTitle(title);
		return this;
	}

	public Field value(String value) {
		setValue(value);
		return this;
	}

	public Field isShort(boolean isShort) {
		setShort(isShort);
		return this;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public boolean isShort() {
		return isShort;
	}

	public void setShort(boolean isShort) {
		this.isShort = isShort;
	}
}
