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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.AllClientPNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.manager.IManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.plugin.slack.entity.Payload;
import com.gitblit.utils.StringUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Configures the final payload and sends a Slack message.
 *
 * @author James Moger
 *
 */
public class Slacker implements IManager {

	private static Slacker instance;

	final Logger log = LoggerFactory.getLogger(getClass());

	final IRuntimeManager runtimeManager;

	final ExecutorService taskPool;

	public static void init(IRuntimeManager manager) {
		if (instance == null) {
			instance = new Slacker(manager);
		}
	}

	public static Slacker instance() {
		return instance;
	}

	Slacker(IRuntimeManager runtimeManager) {
		this.runtimeManager = runtimeManager;
		this.taskPool = Executors.newCachedThreadPool();
	}

	@Override
	public Slacker start() {
		return this;
	}

	@Override
	public Slacker stop() {
		this.taskPool.shutdown();
		return this;
	}

	/**
	 * Returns true if the repository can be posted to Slack.
	 *
	 * @param repository
	 * @return true if the repository can be posted to Slack
	 */
	public boolean shallPost(RepositoryModel repository) {
		boolean postPersonalRepos = runtimeManager.getSettings().getBoolean(Plugin.SETTING_POST_PERSONAL_REPOS, false);
		if (repository.isPersonalRepository() && !postPersonalRepos) {
			return false;
		}
		return true;
	}

	public String getURL() throws IOException {
		String team = runtimeManager.getSettings().getString(Plugin.SETTING_TEAM, null);
		if (StringUtils.isEmpty(team)) {
			throw new IOException(String.format("Could not send message to Slack because '%s' is not defined!", Plugin.SETTING_TEAM));
		}

		String token = runtimeManager.getSettings().getString(Plugin.SETTING_TOKEN, null);
		if (StringUtils.isEmpty(token)) {
			throw new IOException(String.format("Could not send message to Slack because '%s' is not defined!", Plugin.SETTING_TOKEN));
		}

		String hook = runtimeManager.getSettings().getString(Plugin.SETTING_HOOK, "incoming-webhook");
		if (StringUtils.isEmpty(hook)) {
			hook = "incoming-webhook";
		}

		return String.format("https://%s.slack.com/services/hooks/%s?token=%s", team.toLowerCase(), hook, token);
	}

	/**
	 * Optionally sets the channel of the payload based on the repository.
	 *
	 * @param repository
	 * @param payload
	 */
	public void setChannel(RepositoryModel repository, Payload payload) {
		boolean useProjectChannels = runtimeManager.getSettings().getBoolean(Plugin.SETTING_USE_PROJECT_CHANNELS, false);
		if (!useProjectChannels) {
			return;
		}

		if (StringUtils.isEmpty(repository.projectPath)) {
			return;
		}

		String defaultChannel = runtimeManager.getSettings().getString(Plugin.SETTING_DEFAULT_CHANNEL, null);
		if (!StringUtils.isEmpty(defaultChannel)) {
			payload.setChannel(defaultChannel + "-" + repository.projectPath);
		} else {
			payload.setChannel(repository.projectPath);
		}
	}

	/**
	 * Asynchronously send a simple text message.
	 *
	 * @param message
	 * @throws IOException
	 */
	public void sendAsync(String message) {
		sendAsync(new Payload(message));
	}

	/**
	 * Asynchronously send a payload message.
	 *
	 * @param payload
	 * @throws IOException
	 */
	public void sendAsync(final Payload payload) {
		taskPool.submit(new SlackerTask(this, payload));
	}

	/**
	 * Send a simple text message.
	 *
	 * @param message
	 * @throws IOException
	 */
	public void send(String message) throws IOException  {
		send(new Payload(message));
	}

	/**
	 * Send a payload message.
	 *
	 * @param payload
	 * @throws IOException
	 */
	public void send(Payload payload) throws IOException {
		String slackUrl = getURL();

		payload.setUnfurlLinks(true);
		payload.setUsername(Constants.NAME);

		String defaultChannel = runtimeManager.getSettings().getString(Plugin.SETTING_DEFAULT_CHANNEL, null);
		if (!StringUtils.isEmpty(defaultChannel) && StringUtils.isEmpty(payload.getChannel())) {
			// specify the default channel
			if (defaultChannel.charAt(0) != '#' && defaultChannel.charAt(0) != '@') {
				defaultChannel = "#" + defaultChannel;
			}
			// channels must be lowercase
			payload.setChannel(defaultChannel.toLowerCase());
		}

		String defaultEmoji = runtimeManager.getSettings().getString(Plugin.SETTING_DEFAULT_EMOJI, null);
		if (!StringUtils.isEmpty(defaultEmoji)) {
			if (StringUtils.isEmpty(payload.getIconEmoji()) && StringUtils.isEmpty(payload.getIconUrl())) {
				// specify the default emoji
				payload.setIconEmoji(defaultEmoji);
			}
		}

		Gson gson = new GsonBuilder().create();
		String json = gson.toJson(payload);
		log.debug(json);

		HttpClient client = new DefaultHttpClient();
		HttpPost post = new HttpPost(slackUrl);
		post.getParams().setParameter(CoreProtocolPNames.USER_AGENT,
				Constants.NAME + "/" + Constants.getVersion());
		post.getParams().setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET, "UTF-8");

		client.getParams().setParameter(AllClientPNames.CONNECTION_TIMEOUT, 5000);
		client.getParams().setParameter(AllClientPNames.SO_TIMEOUT, 5000);

		List<NameValuePair> nvps = new ArrayList<NameValuePair>(1);
		nvps.add(new BasicNameValuePair("payload",json));

		post.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));

		HttpResponse response = client.execute(post);

		int rc = response.getStatusLine().getStatusCode();

		if (HttpStatus.SC_OK == rc) {
			// replace this with post.closeConnection() after JGit updates to HttpClient 4.2
			post.abort();
		} else {
			String result = null;
			InputStream is = response.getEntity().getContent();
			try {
				byte [] buffer = new byte[8192];
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				int len = 0;
				while ((len = is.read(buffer)) > -1) {
					os.write(buffer, 0, len);
				}
				result = os.toString("UTF-8");
			} finally {
				if (is != null) {
					is.close();
				}
			}

			log.error("Slack plugin sent:");
			log.error(json);
			log.error("Slack returned:");
			log.error(result);

			throw new RuntimeException(String.format("Slack Error (%s): %s", rc, result));
		}
	}

	private static class SlackerTask implements Serializable, Callable<Boolean> {

		private static final long serialVersionUID = 1L;

		final Logger log = LoggerFactory.getLogger(getClass());
		final Slacker slacker;
		final Payload payload;

		public SlackerTask(Slacker slacker, Payload payload) {
			this.slacker = slacker;
			this.payload = payload;
		}

		@Override
		public Boolean call() throws Exception {
			try {
				slacker.send(payload);
				return true;
			} catch (IOException e) {
				log.error("Failed to send asynchronously to Slack!", e);
			}
			return false;
		}
	}
}
