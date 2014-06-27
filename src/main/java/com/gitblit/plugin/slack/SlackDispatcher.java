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

import java.io.IOException;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import ro.fortsoft.pf4j.Extension;

import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.UserModel;
import com.gitblit.plugin.slack.entity.Payload;
import com.gitblit.servlet.GitblitContext;
import com.gitblit.transport.ssh.commands.CommandMetaData;
import com.gitblit.transport.ssh.commands.DispatchCommand;
import com.gitblit.transport.ssh.commands.SshCommand;
import com.gitblit.transport.ssh.commands.UsageExample;
import com.gitblit.transport.ssh.commands.UsageExamples;
import com.gitblit.utils.ActivityUtils;
import com.gitblit.utils.StringUtils;

@Extension
@CommandMetaData(name = "slack", description = "Slack commands")
public class SlackDispatcher extends DispatchCommand {

	@Override
	protected void setup() {
		boolean canAdmin = getContext().getClient().getUser().canAdmin();
		boolean canPost = getContext().getGitblit().getSettings().getBoolean(Plugin.SETTING_ALLOW_USER_POSTS, false);
		if (canAdmin || canPost) {
			register(TestCommand.class);
			register(MessageCommand.class);
		}
	}

	@CommandMetaData(name = "test", description = "Post a test message")
	@UsageExamples(examples = {
			@UsageExample(syntax = "${cmd}", description = "Posts a test message to the default channel"),
			@UsageExample(syntax = "${cmd} #channel", description = "Posts a test message to #channel"),
			@UsageExample(syntax = "${cmd} @james", description = "Posts a test direct message to james")
	})
	public static class TestCommand extends SshCommand {

		@Argument(index = 0, metaVar = "#CHANNEL|@USER", usage = "Destination Channel or User for message")
		String channel;

		/**
		 * Post a test message
		 */
		@Override
		public void run() throws Failure {
		    Payload payload = Payload.instance("Test message sent from Gitblit");
		    payload.iconEmoji(":envelope:");
		    if (!StringUtils.isEmpty(channel)) {
		    	payload.channel(channel);
		    }

		    String displayName = getContext().getClient().getUser().getDisplayName();
		    payload.username(displayName);

			try {
				IRuntimeManager runtimeManager = GitblitContext.getManager(IRuntimeManager.class);
				Slacker.init(runtimeManager);
				Slacker.instance().send(payload);
			} catch (IOException e) {
			    throw new Failure(1, e.getMessage(), e);
			}
		}
	}

	@CommandMetaData(name = "send", aliases = { "post" }, description = "Asynchronously post a message")
	@UsageExamples(examples = {
			@UsageExample(syntax = "${cmd} -m \"'this is a test'\"", description = "Asynchronously posts a message to the default channel"),
			@UsageExample(syntax = "${cmd} #channel -m \"'this is a test'\"", description = "Asynchronously posts a message to #channel"),
			@UsageExample(syntax = "${cmd} @james -m \"'this is a test'\"", description = "Asynchronously posts a direct message to james")
	})
	public static class MessageCommand extends SshCommand {

		@Argument(index = 0, metaVar = "#CHANNEL|@USER", usage = "Destination Channel or User for message")
		String channel;

		@Option(name = "--message", aliases = {"-m" }, metaVar = "-|MESSAGE", required = true)
		String message;

		@Option(name = "--emoji", metaVar = "EMOJI")
		String emoji = null;

		/**
		 * Post a message
		 */
		@Override
		public void run() throws Failure {
		    UserModel user = getContext().getClient().getUser();

			Payload payload = Payload.instance(message);
		    payload.username(user.getDisplayName());
		    payload.unfurlLinks(true);

		    if (!StringUtils.isEmpty(emoji)) {
		    	if (emoji.indexOf("://") > -1) {
		    		payload.iconUrl(emoji);
		    	} else {
		    		// emoji
		    		if (emoji.charAt(0) != ':') {
		    			emoji = ":" + emoji;
		    		}
		    		if (emoji.charAt(emoji.length() - 1) != ':') {
		    			emoji = emoji + ":";
		    		}
		    		payload.iconEmoji(emoji);
		    	}
		    } else {
				if (StringUtils.isEmpty(user.emailAddress)) {
					payload.iconEmoji(":envelope:");
				} else {
					String url = ActivityUtils.getGravatarThumbnailUrl(user.emailAddress, 36);
					payload.iconUrl(url);
				}
		    }

		    if (!StringUtils.isEmpty(channel)) {
		    	payload.channel(channel);
		    }

			IRuntimeManager runtimeManager = GitblitContext.getManager(IRuntimeManager.class);
			Slacker.init(runtimeManager);
		    Slacker.instance().sendAsync(payload);
		}
	}
}

