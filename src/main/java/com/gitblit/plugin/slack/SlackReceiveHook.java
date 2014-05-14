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
import java.text.MessageFormat;
import java.util.Collection;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.Extension;

import com.gitblit.Constants;
import com.gitblit.Keys;
import com.gitblit.extensions.ReceiveHook;
import com.gitblit.git.GitblitReceivePack;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.UserModel;
import com.gitblit.plugin.slack.entity.Payload;
import com.gitblit.servlet.GitblitContext;
import com.gitblit.utils.StringUtils;

/**
 * This hook will post a message to a channel when a ref is updated.
 *
 * @author James Moger
 *
 */
@Extension
public class SlackReceiveHook extends ReceiveHook {

	final String name = getClass().getSimpleName();

	final Logger log = LoggerFactory.getLogger(getClass());

	final Slacker slacker;

	enum RefType {
		BRANCH, TAG
	}

	public SlackReceiveHook() {
		super();

		IRuntimeManager runtimeManager = GitblitContext.getManager(IRuntimeManager.class);
		Slacker.init(runtimeManager);
    	slacker = Slacker.instance();
	}

	@Override
	public void onPreReceive(GitblitReceivePack receivePack, Collection<ReceiveCommand> commands) {
		// NOOP
	}

	@Override
	public void onPostReceive(GitblitReceivePack receivePack, Collection<ReceiveCommand> commands) {
		if (!shallPost(receivePack, commands)) {
			return;
		}

    	IRuntimeManager runtimeManager = GitblitContext.getManager(IRuntimeManager.class);
		try {
			for (ReceiveCommand cmd : commands) {
				RefType rType = null;
				if (cmd.getRefName().startsWith(Constants.R_TAGS)) {
					rType = RefType.TAG;
			    	boolean shallPostTag = runtimeManager.getSettings().getBoolean(Plugin.SETTING_POST_TAGS, true);
			    	if (!shallPostTag) {
			    		continue;
			    	}
				} else if (cmd.getRefName().startsWith(Constants.R_HEADS)) {
					rType = RefType.BRANCH;
			    	boolean shallPostBranch = runtimeManager.getSettings().getBoolean(Plugin.SETTING_POST_BRANCHES, true);
			    	if (!shallPostBranch) {
			    		continue;
			    	}
				} else {
					// ignore other refs
					continue;
				}

				switch (cmd.getType()) {
				case CREATE:
					sendCreate(receivePack, cmd, rType);
					break;
				case UPDATE:
					sendUpdate(receivePack, cmd, rType, true);
					break;
				case UPDATE_NONFASTFORWARD:
					sendUpdate(receivePack, cmd, rType, false);
					break;
				case DELETE:
					sendDelete(receivePack, cmd, rType);
					break;
				}
			}
		} catch (IOException e) {
			log.error("Failed to notify Slack!", e);
		}
	}

	/**
	 * Determine if the ref changes for this repository should be posted to Slack.
	 *
	 * @param receivePack
	 * @return true if the ref changes should be posted
	 */
	protected boolean shallPost(GitblitReceivePack receivePack, Collection<ReceiveCommand> commands) {
		boolean shallPostRepo = slacker.shallPost(receivePack.getRepositoryModel());
		return shallPostRepo;
	}

	/**
	 * Sends a Slack message when a branch or a tag is created.
	 *
	 * @param receivePack
	 * @param cmd
	 * @param rType
	 */
	protected void sendCreate(GitblitReceivePack receivePack, ReceiveCommand cmd, RefType rType) throws IOException {
		UserModel user = receivePack.getUserModel();
		RepositoryModel repo = receivePack.getRepositoryModel();
		String shortRef = Repository.shortenRefName(cmd.getRefName());
		String repoUrl = getUrl(repo.name, null, null);
		String url = getUrl(repo.name, null, cmd.getNewId().getName());

		String msg = String.format("*%s* has created %s <%s|%s> in <%s|%s>", user.getDisplayName(),
    			rType.name().toLowerCase(), url, shortRef, repoUrl, StringUtils.stripDotGit(repo.name));

    	Payload payload = Payload.instance(msg);
    	slacker.setChannel(repo, payload);
    	slacker.sendAsync(payload);
    }

	/**
	 * Sends a Slack message when a branch or a tag has been updated.
	 *
	 * @param receivePack
	 * @param cmd
	 * @param rType
	 * @param isFF
	 */
	protected void sendUpdate(GitblitReceivePack receivePack, ReceiveCommand cmd, RefType rType, boolean isFF) throws IOException {
		UserModel user = receivePack.getUserModel();
		RepositoryModel repo = receivePack.getRepositoryModel();
		String shortRef = Repository.shortenRefName(cmd.getRefName());
		String repoUrl = getUrl(repo.name, null, null);

		String action;
		String url;
		switch (rType) {
		case TAG:
			url = getUrl(repo.name, null, cmd.getNewId().getName());
			action = "*MOVED* tag";
			break;
		default:
			url = getUrl(repo.name, cmd.getOldId().getName(), cmd.getNewId().getName());
			if (isFF) {
				int commits = countCommits(receivePack, cmd.getOldId().name(), cmd.getNewId().name());
				if (commits == 1) {
					action = "pushed 1 commit to";
				} else {
					action = String.format("pushed %d commits to", commits);
				}
			} else {
				action = "*REWRITTEN*";
			}
			break;
		}

		String msg = String.format("*%s* has %s <%s|%s> in <%s|%s>", user.getDisplayName(), action,
				 url, shortRef, repoUrl, StringUtils.stripDotGit(repo.name));

    	Payload payload = Payload.instance(msg);
    	slacker.setChannel(repo, payload);
    	slacker.sendAsync(payload);
	}

	/**
	 * Sends a Slack message when a branch or a tag is deleted.
	 *
	 * @param receivePack
	 * @param cmd
	 * @param rType
	 */
	protected void sendDelete(GitblitReceivePack receivePack, ReceiveCommand cmd, RefType rType) throws IOException {
		UserModel user = receivePack.getUserModel();
		RepositoryModel repo = receivePack.getRepositoryModel();
		String shortRef = Repository.shortenRefName(cmd.getRefName());
		String repoUrl = getUrl(repo.name, null, null);

		String msg = String.format("*%s* has deleted %s *%s* from <%s|%s>", user.getDisplayName(),
    			rType.name().toLowerCase(), shortRef, repoUrl, StringUtils.stripDotGit(repo.name));

    	Payload payload = Payload.instance(msg);
    	slacker.setChannel(repo, payload);
    	slacker.sendAsync(payload);
	}

    /**
     * Returns a link appropriate for the push.
     *
     * If both new and old ids are null, the summary page link is returned.
     *
     * @param repo
     * @param oldId
     * @param newId
     * @return a link
     */
    protected String getUrl(String repo, String oldId, String newId) {
    	IRuntimeManager runtimeManager = GitblitContext.getManager(IRuntimeManager.class);
		String canonicalUrl = runtimeManager.getSettings().getString(Keys.web.canonicalUrl, "https://localhost:8443");

		if (oldId == null && newId != null) {
			// create
			final String hrefPattern = "{0}/commit?r={1}&h={2}";
			return MessageFormat.format(hrefPattern, canonicalUrl, repo, newId);
		} else if (oldId != null && newId != null) {
			// update/compare
			final String hrefPattern = "{0}/compare?r={1}&h={2}..{3}";
			return MessageFormat.format(hrefPattern, canonicalUrl, repo, oldId, newId);
		} else if (oldId == null && newId == null) {
			// summary page
			final String hrefPattern = "{0}/summary?r={1}";
			return MessageFormat.format(hrefPattern, canonicalUrl, repo);
		}

		return null;
    }

    private int countCommits(GitblitReceivePack receivePack, String baseId, String tipId) {
		int count = 0;
		RevWalk walk = receivePack.getRevWalk();
		walk.reset();
		walk.sort(RevSort.TOPO);
		walk.sort(RevSort.REVERSE, true);
		try {
			RevCommit tip = walk.parseCommit(receivePack.getRepository().resolve(tipId));
			RevCommit base = walk.parseCommit(receivePack.getRepository().resolve(baseId));
			walk.markStart(tip);
			walk.markUninteresting(base);
			for (;;) {
				RevCommit c = walk.next();
				if (c == null) {
					break;
				}
				count++;
			}
		} catch (IOException e) {
			// Should never happen, the core receive process would have
			// identified the missing object earlier before we got control.
			log.error("failed to get commit count", e);
			return 0;
		} finally {
			walk.release();
		}
		return count;
	}
}