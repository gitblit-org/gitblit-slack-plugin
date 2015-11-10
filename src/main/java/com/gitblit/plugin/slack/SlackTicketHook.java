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
import static org.pegdown.Extensions.ALL;
import static org.pegdown.Extensions.SMARTYPANTS;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.pegdown.ParsingTimeoutException;
import org.pegdown.PegDownProcessor;
import org.pegdown.ast.RootNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.Extension;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.extensions.TicketHook;
import com.gitblit.manager.IGitblit;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.TicketModel.Patchset;
import com.gitblit.models.UserModel;
import com.gitblit.plugin.slack.entity.Attachment;
import com.gitblit.plugin.slack.entity.Field;
import com.gitblit.plugin.slack.entity.Payload;
import com.gitblit.servlet.GitblitContext;
import com.gitblit.utils.ActivityUtils;
import com.gitblit.utils.StringUtils;

/**
 * This hook will post a message to a channel when a ticket is created or updated.
 *
 * @author James Moger
 *
 */
@Extension
public class SlackTicketHook extends TicketHook {

	final String name = getClass().getSimpleName();

	final Logger log = LoggerFactory.getLogger(getClass());

	final Slacker slacker;

	final IStoredSettings settings;

	public SlackTicketHook() {
		super();

		IRuntimeManager runtimeManager = GitblitContext.getManager(IRuntimeManager.class);
		Slacker.init(runtimeManager);
    	slacker = Slacker.instance();

    	settings = runtimeManager.getSettings();
	}

    @Override
    public void onNewTicket(TicketModel ticket) {
    	if (!shallPost(ticket)) {
			return;
		}

		Set<TicketModel.Field> fieldExclusions = new HashSet<TicketModel.Field>();
		fieldExclusions.addAll(Arrays.asList(TicketModel.Field.watchers, TicketModel.Field.voters,
				TicketModel.Field.status, TicketModel.Field.mentions));

    	Change change = ticket.changes.get(0);
    	IUserManager userManager = GitblitContext.getManager(IUserManager.class);
    	boolean postAsUser = settings.getBoolean(Plugin.SETTING_POST_AS_USER, true);

    	UserModel user = userManager.getUserModel(change.author);
    	String author;
    	if (postAsUser) {
    		// posting as user, do not BOLD username
    		author = user.getDisplayName();
    	} else {
    		// posting as Gitblit, BOLD username to draw attention
    		author = "*" + user.getDisplayName() + "*";
    	}

    	String msg = String.format("%s has created *%s* <%s|ticket-%s>", author,
    			StringUtils.stripDotGit(ticket.repository), getUrl(ticket), ticket.number);

    	Payload payload = Payload
    			.instance(msg)
                .attachments(fields(ticket, change, fieldExclusions));
    	attribute(payload, user);

   		slacker.sendAsync(payload);
    }

    @Override
    public void onUpdateTicket(TicketModel ticket, Change change) {
    	if (!shallPost(ticket)) {
			return;
		}
		Set<TicketModel.Field> fieldExclusions = new HashSet<TicketModel.Field>();
		fieldExclusions.addAll(Arrays.asList(TicketModel.Field.watchers, TicketModel.Field.voters,
				TicketModel.Field.mentions, TicketModel.Field.title, TicketModel.Field.body,
				TicketModel.Field.mergeSha));

		IUserManager userManager = GitblitContext.getManager(IUserManager.class);
    	boolean postAsUser = settings.getBoolean(Plugin.SETTING_POST_AS_USER, true);

		UserModel user = userManager.getUserModel(change.author);
		String author;
    	if (postAsUser) {
    		// posting as user, do not BOLD username
    		author = user.getDisplayName();
    	} else {
    		// posting as Gitblit, BOLD username to draw attention
    		author = "*" + user.getDisplayName() + "*";
    	}

		String url = String.format("<%s|ticket-%s>", getUrl(ticket), ticket.number);
		String repo = "*" + StringUtils.stripDotGit(ticket.repository) + "*";
		String msg = null;

		if (change.hasReview()) {
			/*
			 * Patchset review
			 */
    		msg = String.format("%s has reviewed %s %s patchset %s-%s", author, repo, url,
    				change.review.patchset, change.review.rev);

		} else if (change.hasPatchset()) {
			/*
			 * New Patchset
			 */
			String tip = change.patchset.tip;
			String base;
			String leadIn;
			if (change.patchset.rev == 1) {
				if (change.patchset.number == 1) {
					/*
					 * Initial proposal
					 */
					leadIn = String.format("%s has pushed a proposal for %s %s", author, repo, url);
				} else {
					/*
					 * Rewritten patchset
					 */
					leadIn = String.format("%s has rewritten the patchset for %s %s (%s)",
							author, repo, url, change.patchset.type);
				}
				base = change.patchset.base;
			} else {
				/*
				 * Fast-forward patchset update
				 */
				leadIn = String.format("%s has added %s %s to %s %s", author, change.patchset.added,
						change.patchset.added == 1 ? "commit" : "commits", repo, url);
				Patchset prev = ticket.getPatchset(change.patchset.number, change.patchset.rev - 1);
				base = prev.tip;
			}

			StringBuilder sb = new StringBuilder();
			sb.append(leadIn);

			// abbreviated commit list
			List<RevCommit> commits = getCommits(ticket.repository, base, tip);
			sb.append("\n\n");
			int shortIdLen = settings.getInteger(Keys.web.shortCommitIdLength, 6);
			int maxCommits = 5;
			for (int i = 0; i < Math.min(maxCommits, commits.size()); i++) {
				RevCommit commit = commits.get(i);
				String commitUrl = getUrl(ticket.repository, null, commit.getName());
				String shortId = commit.getName().substring(0, shortIdLen);
				String shortMessage = StringUtils.trimString(commit.getShortMessage(), Constants.LEN_SHORTLOG);
				String row = String.format("<%s|`%s`> %s\n",
						commitUrl, shortId, shortMessage);
				sb.append(row);
			}

			// compare link
			if (commits.size() > 1) {
				String compareUrl = getUrl(ticket.repository, base, tip);
				String compareText;
				if (commits.size() > maxCommits) {
					int diff = commits.size() - maxCommits;
					if (diff == 1) {
						compareText = "1 more commit";
					} else {
						compareText = String.format("%d more commits", diff);
					}
				} else {
					compareText = String.format("view comparison of these %s commits", commits.size());
				}
				sb.append("\n");
				sb.append(String.format("<%s|%s>", compareUrl, compareText));
			}

			msg = sb.toString();
		} else if (change.isMerge()) {
			/*
			 * Merged
			 */
			msg = String.format("%s has merged %s %s to *%s*", author, repo, url, ticket.mergeTo);
		} else if (change.isStatusChange()) {
			/*
			 * Status Change
			 */
			msg = String.format("%s has changed the status of %s %s", author, repo, url);
		} else if (change.hasComment() && settings.getBoolean(Plugin.SETTING_POST_TICKET_COMMENTS, true)) {
			/*
			 * Comment
			 */
			msg = String.format("%s has commented on %s %s", author, repo, url);
		}

		if (msg == null) {
			// not a change we are reporting
			return;
		}

		Payload payload = Payload
				.instance(msg)
				.attachments(fields(ticket, change, fieldExclusions));
		attribute(payload, user);

		IRepositoryManager repositoryManager = GitblitContext.getManager(IRepositoryManager.class);
		RepositoryModel repository = repositoryManager.getRepositoryModel(ticket.repository);
   		slacker.setChannel(repository, payload);
   		slacker.sendAsync(payload);
    }

	/**
	 * Optionally stamp the payload with an emoji, icon url, or user attributions.
	 *
	 * @param payload
	 * @param user
	 */
	protected void attribute(Payload payload, UserModel user) {
		IRuntimeManager runtimeManager = GitblitContext.getManager(IRuntimeManager.class);
    	String icon = runtimeManager.getSettings().getString(Plugin.SETTING_TICKET_EMOJI, null);
    	String defaultIcon = runtimeManager.getSettings().getString(Plugin.SETTING_DEFAULT_EMOJI, null);
    	if (StringUtils.isEmpty(icon)) {
    		icon = defaultIcon;
    	}

    	// set the username and gravatar
    	boolean postAsUser = runtimeManager.getSettings().getBoolean(Plugin.SETTING_POST_AS_USER, true);
    	if (postAsUser) {
    		payload.username(user.getDisplayName());
    		if (!StringUtils.isEmpty(user.emailAddress)) {
    			icon = ActivityUtils.getGravatarThumbnailUrl(user.emailAddress, 36);
    		}
		}

		payload.icon(icon);
	}

    protected Attachment fields(TicketModel ticket, Change change, Set<TicketModel.Field> fieldExclusions) {
    	Map<TicketModel.Field, String> filtered = new HashMap<TicketModel.Field, String>();
    	if (change.hasFieldChanges()) {
    		for (Map.Entry<TicketModel.Field, String> fc : change.fields.entrySet()) {
    			if (!fieldExclusions.contains(fc.getKey())) {
    				// field is included
    				filtered.put(fc.getKey(), fc.getValue());
    			}
    		}
    	}

    	// ensure we have some basic context fields
    	if (!filtered.containsKey(TicketModel.Field.title)) {
    		filtered.put(TicketModel.Field.title, ticket.title);
    	}

    	String text = null;
    	String color = null;
    	if (change.isStatusChange()) {
    		// status change
    		switch (ticket.status) {
    		case Abandoned:
    		case Declined:
    		case Invalid:
    		case Wontfix:
    		case Duplicate:
    			color = "danger";
    			break;
    		case On_Hold:
    			color = "warning";
    			break;
    		case Closed:
    		case Fixed:
    		case Merged:
    		case Resolved:
    			color = "good";
    			break;
    		case New:
    		default:
    			color = null;
    		}
    	} else if (change.hasComment() && settings.getBoolean(Plugin.SETTING_POST_TICKET_COMMENTS, true)) {
    		// transform Markdown comment
    		text = renderMarkdown(change.comment.text, ticket.repository);
    	}

    	// sort by field ordinal
    	List<TicketModel.Field> fields = new ArrayList<TicketModel.Field>(filtered.keySet());
    	Collections.sort(fields);
    	Attachment attachment = Attachment.instance(ticket.title)
    			.color(color)
    			.text(text);
    	if (fields.size() > 0) {
    		for (TicketModel.Field field : fields) {
    			boolean isShort = TicketModel.Field.title != field && TicketModel.Field.body != field;
    			boolean isMrkdwn = false;
    			String value;
    			if (change.getField(field) == null) {
    				continue;
    			} else {
    				value = change.getField(field);
    				value = filtered.get(field);

    				if (TicketModel.Field.body == field) {
    					// transform the body to Slack markup
    					value = renderMarkdown(value, ticket.repository);
    					isMrkdwn = true;
    				} else if (TicketModel.Field.responsible == field) {
    					// lookup display name of the user
    					value = getDisplayName(value);
    				}

    				if (!StringUtils.isEmpty(value)) {
    					attachment.addField(Field.instance(field.toString(), value).isShort(isShort).isMrkdwn(isMrkdwn));
    				}
    			}
    		}
    	}
    	return attachment;
    }

    protected String renderMarkdown(String markdown, String repository) {
    	if (StringUtils.isEmpty(markdown)) {
    		return markdown;
    	}

    	// extract blockquotes, insert placeholders, and render the blockquotes individually
    	final String placeholder = "!!BLOCKQUOTE!!";
    	List<String> list = new ArrayList<>();
    	StringBuilder sb = new StringBuilder();
    	StringBuilder bq = new StringBuilder();
    	for (String line : markdown.split("\n")) {
    		if (line.length() > 0) {
    			if (line.startsWith("> ")) {
    				// accumulate blockquotes
    				boolean newBQ = bq.length() == 0;
    				bq.append(line.substring(2)).append('\n');
    				if (newBQ) {
    					// insert a placeholder
    					sb.append(placeholder).append(list.size()).append('\n');
    				}
    				continue;
    			} else if (bq.length() > 0) {
    				// render blockquote by itself and reinject blockquote syntax
    				String quote = bq.toString();
    				String rendered = renderMarkdown(quote, repository);
    				bq.setLength(0);
    				StringBuilder rsb = new StringBuilder();
    				for (String rl : rendered.split("\n")) {
    					rsb.append("> ").append(rl).append('\n');
    				}
    				list.add(rsb.toString());
    			}
    		}
    		sb.append(line).append('\n');
    	}

    	String text = sb.toString();

    	try {
    		IRuntimeManager runtimeManager = GitblitContext.getManager(IRuntimeManager.class);
    		String canonicalUrl = runtimeManager.getSettings().getString(Keys.web.canonicalUrl, "https://localhost:8443");

    		// emphasize and link mentions
    		String mentionReplacement = String.format(" **[@$1](%1s/user/$1)**", canonicalUrl);
    		text = text.replaceAll("\\s@([A-Za-z0-9-_]+)", mentionReplacement);

    		// link ticket refs
    		String ticketReplacement = MessageFormat.format("$1[#$2]({0}/tickets?r={1}&h=$2)$3", canonicalUrl, repository);
    		text = text.replaceAll("([\\s,]+)#(\\d+)([\\s,:\\.\\n])", ticketReplacement);

    		// link commit shas
    		int shaLen = settings.getInteger(Keys.web.shortCommitIdLength, 6);
    		String commitPattern = MessageFormat.format("\\s([A-Fa-f0-9]'{'{0}'}')([A-Fa-f0-9]'{'{1}'}')", shaLen, 40 - shaLen);
    		String commitReplacement = String.format(" [`$1`](%1$s/commit\\?r=%2$s&h=$1$2)", canonicalUrl, repository);
    		text = text.replaceAll(commitPattern, commitReplacement);

			PegDownProcessor pd = new PegDownProcessor(ALL & ~SMARTYPANTS);
			RootNode astRoot = pd.parseMarkdown(text.toCharArray());
			String slackMarkup = new SlackMarkupSerializer().toHtml(astRoot);
			slackMarkup = slackMarkup.replace("<pre><code>", "```\n");
			slackMarkup = slackMarkup.replace("</code></pre>", "```\n");

			// re-insert blockquotes
			for (int i = 0; i < list.size(); i++) {
				String quote = list.get(i);
				slackMarkup = slackMarkup.replace(placeholder + i, quote);
			}
			return slackMarkup;
		} catch (ParsingTimeoutException e) {
			log.error(null, e);
			return markdown;
		}
    }

    protected String getDisplayName(String username) {
    	if (StringUtils.isEmpty(username)) {
    		return username;
    	}

		IUserManager userManager = GitblitContext.getManager(IUserManager.class);
		UserModel user = userManager.getUserModel(username);
		if (user != null) {
			String displayName = user.getDisplayName();
			if (!StringUtils.isEmpty(displayName) && !username.equals(displayName)) {
				return displayName;
			}
		}
		return username;
    }

    /**
     * Determine if a ticket should be posted to a Slack channel.
     *
     * @param ticket
     * @return true if the ticket should be posted to a Slack channel
     */
    protected boolean shallPost(TicketModel ticket) {
    	IRuntimeManager runtimeManager = GitblitContext.getManager(IRuntimeManager.class);
    	boolean shallPostTicket = runtimeManager.getSettings().getBoolean(Plugin.SETTING_POST_TICKETS, true);
    	if (!shallPostTicket) {
    		return false;
    	}

		IRepositoryManager repositoryManager = GitblitContext.getManager(IRepositoryManager.class);
		RepositoryModel repository = repositoryManager.getRepositoryModel(ticket.repository);
		boolean shallPostRepo = slacker.shallPost(repository);
		return shallPostRepo;
    }

    protected String getUrl(TicketModel ticket) {
    	return GitblitContext.getManager(IGitblit.class).getTicketService().getTicketUrl(ticket);
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
		} else if (oldId != null && newId == null) {
			// log
			final String hrefPattern = "{0}/log?r={1}&h={2}";
			return MessageFormat.format(hrefPattern, canonicalUrl, repo, oldId);
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

	private List<RevCommit> getCommits(String repositoryName, String baseId, String tipId) {
		IRepositoryManager repositoryManager = GitblitContext.getManager(IRepositoryManager.class);
		List<RevCommit> list = new ArrayList<RevCommit>();
		try (Repository db = repositoryManager.getRepository(repositoryName)) {
			try (RevWalk walk = new RevWalk(db)) {
				walk.reset();
				walk.sort(RevSort.TOPO);
				walk.sort(RevSort.REVERSE, true);
				RevCommit tip = walk.parseCommit(db.resolve(tipId));
				RevCommit base = walk.parseCommit(db.resolve(baseId));
				walk.markStart(tip);
				walk.markUninteresting(base);
				for (;;) {
					RevCommit c = walk.next();
					if (c == null) {
						break;
					}
					list.add(c);
				}
			} catch (IOException e) {
				// Should never happen, the core receive process would have
				// identified the missing object earlier before we got control.
				log.error("failed to get commits", e);
			}
		}
		return list;
	}
}