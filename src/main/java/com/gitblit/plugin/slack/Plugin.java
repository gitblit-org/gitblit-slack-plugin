/*
 * Copyright 2014 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.plugin.slack;

import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.Version;

import com.gitblit.extensions.GitblitPlugin;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.servlet.GitblitContext;

public class Plugin extends GitblitPlugin {

	public static final String SETTING_URL = "slack.url";

	public static final String SETTING_POST_AS_USER = "slack.postAsUser";

	public static final String SETTING_DEFAULT_CHANNEL = "slack.defaultChannel";

	public static final String SETTING_USE_PROJECT_CHANNELS = "slack.useProjectChannels";

	public static final String SETTING_DEFAULT_EMOJI = "slack.defaultEmoji";

	public static final String SETTING_TICKET_EMOJI = "slack.ticketEmoji";

	public static final String SETTING_GIT_EMOJI = "slack.gitEmoji";

	public static final String SETTING_ALLOW_USER_POSTS = "slack.allowUserPosts";

	public static final String SETTING_POST_PERSONAL_REPOS = "slack.postPersonalRepos";

	public static final String SETTING_POST_TICKETS = "slack.postTickets";

	public static final String SETTING_POST_TICKET_COMMENTS = "slack.postTicketComments";

	public static final String SETTING_POST_BRANCHES = "slack.postBranches";

	public static final String SETTING_POST_TAGS = "slack.postTags";

	public Plugin(PluginWrapper wrapper) {
		super(wrapper);

		IRuntimeManager runtimeManager = GitblitContext.getManager(IRuntimeManager.class);
		Slacker.init(runtimeManager);
	}

	@Override
	public void start() {
		log.debug("{} STARTED.", getWrapper().getPluginId());
	}

	@Override
	public void stop() {
		Slacker.instance().stop();
		log.debug("{} STOPPED.", getWrapper().getPluginId());
	}

	@Override
	public void onInstall() {
		log.debug("{} INSTALLED.", getWrapper().getPluginId());
	}

	@Override
	public void onUpgrade(Version oldVersion) {
		log.debug("{} UPGRADED from {}.", getWrapper().getPluginId(), oldVersion);
	}

	@Override
	public void onUninstall() {
		log.debug("{} UNINSTALLED.", getWrapper().getPluginId());
	}
}
