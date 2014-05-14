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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.Extension;

import com.gitblit.IStoredSettings;
import com.gitblit.extensions.TicketHook;
import com.gitblit.manager.IGitblit;
import com.gitblit.manager.IRepositoryManager;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.manager.IUserManager;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.TicketModel;
import com.gitblit.models.TicketModel.Change;
import com.gitblit.models.UserModel;
import com.gitblit.plugin.slack.entity.Attachment;
import com.gitblit.plugin.slack.entity.Field;
import com.gitblit.plugin.slack.entity.Payload;
import com.gitblit.servlet.GitblitContext;
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

    	UserModel reporter = userManager.getUserModel(change.author);
    	String msg = String.format("*%s* has created *%s* <%s|ticket-%s>", reporter.getDisplayName(),
    			StringUtils.stripDotGit(ticket.repository), getUrl(ticket), ticket.number);

    	Payload payload = Payload.instance(msg)
                .attachments(fields(ticket, change, fieldExclusions));

   		slacker.sendAsync(payload);
    }

    @Override
    public void onUpdateTicket(TicketModel ticket, Change change) {
    	if (!shallPost(ticket)) {
			return;
		}
		Set<TicketModel.Field> fieldExclusions = new HashSet<TicketModel.Field>();
		fieldExclusions.addAll(Arrays.asList(TicketModel.Field.watchers, TicketModel.Field.voters,
				TicketModel.Field.mentions, TicketModel.Field.title, TicketModel.Field.body));

		IUserManager userManager = GitblitContext.getManager(IUserManager.class);
		String author = "*" + userManager.getUserModel(change.author).getDisplayName() + "*";
		String url = String.format("<%s|ticket-%s>", getUrl(ticket), ticket.number);
		String repo = "*" + StringUtils.stripDotGit(ticket.repository) + "*";
		String msg = null;
		String emoji = null;

		if (change.hasReview()) {
			/*
			 * Patchset review
			 */
    		msg = String.format("%s has reviewed %s %s patchset %s-%s", author, repo, url,
    				change.patchset.number, change.patchset.rev);
    		switch (change.review.score) {
    		case approved:
    			emoji = ":white_check_mark:";
    			break;
    		case looks_good:
    			emoji = ":thumbsup:";
    			break;
    		case needs_improvement:
    			emoji = ":thumbsdown:";
    			break;
    		case vetoed:
    			emoji = ":no_entry_sign:";
    			break;
    		default:
    			break;
    		}
		} else if (change.hasPatchset()) {
			/*
			 * New Patchset
			 */
			if (change.patchset.rev == 1) {
				if (change.patchset.number == 1) {
					/*
					 * Initial proposal
					 */
					msg = String.format("%s has proposed commits for %s %s", author, repo, url);
				} else {
					/*
					 * Rewritten patchset
					 */
					msg = String.format("%s has rewritten the patchset for %s %s", author, repo, url);
				}
			} else {
				/*
				 * Fast-forward patchset update
				 */
				msg = String.format("%s has added %s %s to %s %s", author, change.patchset.added,
						change.patchset.added == 1 ? "commit" : "commits", repo, url);
			}
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

		Payload payload = Payload.instance(msg)
				.iconEmoji(emoji)
				.attachments(fields(ticket, change, fieldExclusions));

   		String project = StringUtils.getRootPath(ticket.repository);
   		slacker.setProjectChannel(project, payload);
   		slacker.sendAsync(payload);
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
    			color= "danger";
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
    			text = ticket.body;
    		default:
    			color = null;
    		}
    	} else if (change.hasComment() && settings.getBoolean(Plugin.SETTING_POST_TICKET_COMMENTS, true)) {
    		// comment
    		text = change.comment.text;
    	}

    	// sort by field ordinal
    	List<TicketModel.Field> fields = new ArrayList<TicketModel.Field>(filtered.keySet());
    	Collections.sort(fields);
    	Attachment attachment = Attachment.instance(ticket.title)
    			.pretext(ticket.title)
    			.color(color)
    			.text(text);
    	if (fields.size() > 0) {
    		for (TicketModel.Field field : fields) {
    			boolean isShort = TicketModel.Field.title != field && TicketModel.Field.body != field;
    			attachment.addField(Field.instance(field.toString(), change.getField(field)).isShort(isShort));
    		}
    	}
    	return attachment;
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
}