/*
 * Copyright (c) 2003, Rafael Steil
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met:
 * 
 * 1) Redistributions of source code must retain the above 
 * copyright notice, this list of conditions and the 
 * following  disclaimer.
 * 2)  Redistributions in binary form must reproduce the 
 * above copyright notice, this list of conditions and 
 * the following disclaimer in the documentation and/or 
 * other materials provided with the distribution.
 * 3) Neither the name of "Rafael Steil" nor 
 * the names of its contributors may be used to endorse 
 * or promote products derived from this software without 
 * specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT 
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY 
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, 
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR 
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL 
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE 
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, 
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER 
 * IN CONTRACT, STRICT LIABILITY, OR TORT 
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN 
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
 * 
 * This file creation date: May 3, 2003 / 5:05:18 PM
 * The JForum Project
 * http://www.jforum.net
 */
package net.jforum.view.forum;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.jforum.Command;
import net.jforum.JForum;
import net.jforum.SessionFacade;
import net.jforum.entities.Attachment;
import net.jforum.entities.Post;
import net.jforum.entities.QuotaLimit;
import net.jforum.entities.Topic;
import net.jforum.entities.User;
import net.jforum.entities.UserSession;
import net.jforum.exceptions.AttachmentException;
import net.jforum.model.AttachmentModel;
import net.jforum.model.DataAccessDriver;
import net.jforum.model.ForumModel;
import net.jforum.model.PostModel;
import net.jforum.model.TopicModel;
import net.jforum.model.UserModel;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.PostRepository;
import net.jforum.repository.RankingRepository;
import net.jforum.repository.SecurityRepository;
import net.jforum.repository.SmiliesRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.security.PermissionControl;
import net.jforum.security.SecurityConstants;
import net.jforum.util.I18n;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;
import net.jforum.view.forum.common.AttachmentCommon;
import net.jforum.view.forum.common.ForumCommon;
import net.jforum.view.forum.common.PostCommon;
import net.jforum.view.forum.common.TopicsCommon;
import net.jforum.view.forum.common.ViewCommon;

import org.apache.log4j.Logger;

/**
 * @author Rafael Steil
 * @version $Id: PostAction.java,v 1.68.6.1 2005/03/28 15:59:53 rafaelsteil Exp $
 */
public class PostAction extends Command {
	private static final Logger logger = Logger.getLogger(PostAction.class);

	public void list() throws Exception {
		PostModel pm = DataAccessDriver.getInstance().newPostModel();
		UserModel um = DataAccessDriver.getInstance().newUserModel();
		TopicModel tm = DataAccessDriver.getInstance().newTopicModel();

		int userId = SessionFacade.getUserSession().getUserId();
		int anonymousUser = SystemGlobals.getIntValue(ConfigKeys.ANONYMOUS_USER_ID);

		int topicId = this.request.getIntParameter("topic_id");
		Topic topic = tm.selectById(topicId);

		// The topic exists?
		if (topic.getId() == 0) {
			this.topicNotFound();
			return;
		}

		// Shall we proceed?
		if (!TopicsCommon.isTopicAccessible(topic.getForumId())) {
			return;
		}

		int count = SystemGlobals.getIntValue(ConfigKeys.POST_PER_PAGE);
		int start = ViewCommon.getStartPage();

		PermissionControl pc = SecurityRepository.get(userId);

		boolean canEdit = false;
		if (pc.canAccess(SecurityConstants.PERM_MODERATION_POST_EDIT)) {
			canEdit = true;
		}

		Map usersMap = new HashMap();
		List helperList = PostCommon.topicPosts(pm, um, usersMap, canEdit, userId, topic.getId(), start, count);
		
		// Ugly assumption:
		// Is moderation pending for the topic?
		if (topic.isModerated() && helperList.size() == 0) {
			this.notModeratedYet();
			return;
		}

		boolean isModerator = (pc.canAccess(SecurityConstants.PERM_MODERATION))
				&& (pc.canAccess(SecurityConstants.PERM_MODERATION_FORUMS, Integer.toString(topic.getForumId())));

		// Set the topic status as read
		tm.updateReadStatus(topic.getId(), userId, true);
		
		tm.incrementTotalViews(topic.getId());

		if (userId != anonymousUser) {
			((HashMap) SessionFacade.getAttribute(ConfigKeys.TOPICS_TRACKING)).put(new Integer(topic.getId()),
					new Long(topic.getLastPostTimeInMillis().getTime()));
		}
		
		this.context.put("attachmentsEnabled", SecurityRepository.canAccess(
				SecurityConstants.PERM_ATTACHMENTS_ENABLED, Integer.toString(topic.getForumId())));
		this.context.put("canDownloadAttachments", SecurityRepository.canAccess(
				SecurityConstants.PERM_ATTACHMENTS_DOWNLOAD));
		this.context.put("am", new AttachmentCommon(this.request));
		this.context.put("karmaVotes", DataAccessDriver.getInstance().newKarmaModel().getUserVotes(topic.getId(), userId));
		this.context.put("rssEnabled", SystemGlobals.getBoolValue(ConfigKeys.RSS_ENABLED));
		this.context.put("canRemove",
				SecurityRepository.canAccess(SecurityConstants.PERM_MODERATION_POST_REMOVE));
		this.context.put("canEdit", canEdit);
		this.context.put("moduleAction", "post_show.htm");
		this.context.put("allCategories", ForumCommon.getAllCategoriesAndForums(false));
		this.context.put("topic", topic);
		this.context.put("rank", new RankingRepository());
		this.context.put("posts", helperList);
		this.context.put("karmaEnabled", SecurityRepository.canAccess(SecurityConstants.PERM_KARMA_ENABLED));
		this.context.put("forum", ForumRepository.getForum(topic.getForumId()));
		this.context.put("users", usersMap);
		this.context.put("topicId", new Integer(topicId));
		this.context.put("anonymousPosts", SecurityRepository.canAccess(SecurityConstants.PERM_ANONYMOUS_POST, 
				Integer.toString(topic.getForumId())));
		this.context.put("watching", tm.isUserSubscribed(topicId, SessionFacade.getUserSession().getUserId()));
		this.context.put("pageTitle", SystemGlobals.getValue(ConfigKeys.FORUM_NAME) + " - " + topic.getTitle());
		this.context.put("isAdmin", SecurityRepository.canAccess(SecurityConstants.PERM_ADMINISTRATION));
		this.context.put("readonly", !SecurityRepository.canAccess(SecurityConstants.PERM_READ_ONLY_FORUMS, 
				Integer.toString(topic.getForumId())));
		this.context.put("replyOnly", !SecurityRepository.canAccess(SecurityConstants.PERM_REPLY_ONLY, 
				Integer.toString(topic.getForumId())));

		this.context.put("isModerator", SecurityRepository.canAccess(SecurityConstants.PERM_MODERATION)
						&& SecurityRepository.canAccess(SecurityConstants.PERM_MODERATION_FORUMS, 
								Integer.toString(topic.getForumId())));

		// Topic Status
		this.context.put("STATUS_LOCKED", new Integer(Topic.STATUS_LOCKED));
		this.context.put("STATUS_UNLOCKED", new Integer(Topic.STATUS_UNLOCKED));

		// Pagination
		int totalPosts = tm.getTotalPosts(topic.getId());
		this.context.put("totalPages", new Double(Math.ceil((double) totalPosts / (double) count)));
		this.context.put("recordsPerPage", new Integer(count));
		this.context.put("totalRecords", new Integer(totalPosts));
		this.context.put("thisPage", new Double(Math.ceil((double) (start + 1) / (double) count)));
		this.context.put("start", new Integer(start));
	}

	public void review() throws Exception {
		PostModel pm = DataAccessDriver.getInstance().newPostModel();
		UserModel um = DataAccessDriver.getInstance().newUserModel();
		TopicModel tm = DataAccessDriver.getInstance().newTopicModel();

		int userId = SessionFacade.getUserSession().getUserId();
		int topicId = this.request.getIntParameter("topic_id");
		Topic topic = tm.selectById(topicId);

		if (!TopicsCommon.isTopicAccessible(topic.getForumId())) {
			return;
		}

		int count = SystemGlobals.getIntValue(ConfigKeys.POST_PER_PAGE);
		int start = ViewCommon.getStartPage();

		Map usersMap = new HashMap();
		List helperList = PostCommon.topicPosts(pm, um, usersMap, false, userId, topic.getId(), start, count);
		Collections.reverse(helperList);

		this.setTemplateName(SystemGlobals.getValue(ConfigKeys.TEMPLATE_NAME) + "/empty.htm");

		this.context.put("moduleAction", "topic_review.htm");
		this.context.put("posts", helperList);
		this.context.put("users", usersMap);
	}

	private void topicNotFound() {
		this.context.put("moduleAction", "message.htm");
		this.context.put("message", I18n.getMessage("PostShow.TopicNotFound"));
	}

	private void postNotFound() {
		this.context.put("moduleAction", "message.htm");
		this.context.put("message", I18n.getMessage("PostShow.PostNotFound"));
	}
	
	private void replyOnly()
	{
		this.context.put("moduleAction", "message.htm");
		this.context.put("message", I18n.getMessage("PostShow.replyOnly"));
	}
	
	private boolean isReplyOnly(int forumId) throws Exception
	{
		return !SecurityRepository.canAccess(SecurityConstants.PERM_REPLY_ONLY, 
				Integer.toString(forumId));
	}

	public void insert() throws Exception {
		int forumId = this.request.getIntParameter("forum_id");

		if (!this.anonymousPost(forumId)
				|| this.isForumReadonly(forumId, this.request.getParameter("topic_id") != null)) {
			return;
		}

		ForumModel fm = DataAccessDriver.getInstance().newForumModel();

		if (this.request.getParameter("topic_id") != null) {
			int topicId = this.request.getIntParameter("topic_id");
			Topic t = DataAccessDriver.getInstance().newTopicModel().selectById(topicId);

			if (t.getStatus() == Topic.STATUS_LOCKED) {
				this.topicLocked();
				return;
			}

			this.context.put("topic", t);
			this.context.put("setType", false);
		}
		else {
			if (this.isReplyOnly(forumId)) {
				this.replyOnly();
				return;
			}
			this.context.put("setType", true);
		}
		
		int userId = SessionFacade.getUserSession().getUserId();

		this.context.put("attachmentsEnabled", SecurityRepository.canAccess(
				SecurityConstants.PERM_ATTACHMENTS_ENABLED, Integer.toString(forumId)));
		
		QuotaLimit ql = new AttachmentCommon(this.request).getQuotaLimit(userId);
		this.context.put("maxAttachmentsSize", new Long(ql != null ? ql.getSizeInBytes() : 1));
		
		this.context.put("maxAttachments", SystemGlobals.getValue(ConfigKeys.ATTACHMENTS_MAX_POST));
		this.context.put("forum", ForumRepository.getForum(forumId));
		this.context.put("action", "insertSave");
		this.context.put("moduleAction", "post_form.htm");
		this.context.put("start", this.request.getParameter("start"));
		this.context.put("isNewPost", true);
		this.context.put("htmlAllowed",
				SecurityRepository.canAccess(SecurityConstants.PERM_HTML_DISABLED, Integer.toString(forumId)));
		this.context.put("canCreateStickyOrAnnouncementTopics",
				SecurityRepository.canAccess(SecurityConstants.PERM_CREATE_STICKY_ANNOUNCEMENT_TOPICS));

		User user = DataAccessDriver.getInstance().newUserModel().selectById(userId);
		user.setSignature(PostCommon.processText(user.getSignature()));
		user.setSignature(PostCommon.processSmilies(user.getSignature(), SmiliesRepository.getSmilies()));

		if (this.request.getParameter("preview") != null) {
			user.setNotifyOnMessagesEnabled(this.request.getParameter("notify") != null);
		}

		this.context.put("user", user);
	}

	public void edit() throws Exception {
		this.edit(false, null);
	}

	private void edit(boolean preview, Post p) throws Exception {
		int userId = SessionFacade.getUserSession().getUserId();
		int aId = SystemGlobals.getIntValue(ConfigKeys.ANONYMOUS_USER_ID);
		boolean canAccess = false;

		if (!preview) {
			PostModel pm = DataAccessDriver.getInstance().newPostModel();
			p = pm.selectById(this.request.getIntParameter("post_id"));

			// The post exist?
			if (p.getId() == 0) {
				this.postNotFound();
				return;
			}
		}

		boolean isModerator = SecurityRepository.canAccess(SecurityConstants.PERM_MODERATION_POST_EDIT);
		canAccess = (isModerator || p.getUserId() == userId);

		if ((userId != aId) && canAccess) {
			Topic topic = DataAccessDriver.getInstance().newTopicModel().selectById(p.getTopicId());

			if (!TopicsCommon.isTopicAccessible(topic.getForumId())) {
				return;
			}

			if (topic.getStatus() == Topic.STATUS_LOCKED && !isModerator) {
				this.topicLocked();
				return;
			}

			if (preview && this.request.getParameter("topic_type") != null) {
				topic.setType(this.request.getIntParameter("topic_type"));
			}
			
			if (p.hasAttachments()) {
				this.context.put("attachments", 
						DataAccessDriver.getInstance().newAttachmentModel().selectAttachments(p.getId()));
			}

			this.context.put("attachmentsEnabled", SecurityRepository.canAccess(
					SecurityConstants.PERM_ATTACHMENTS_ENABLED, Integer.toString(p.getForumId())));
			
			QuotaLimit ql = new AttachmentCommon(this.request).getQuotaLimit(userId);
			this.context.put("maxAttachmentsSize", new Long(ql != null ? ql.getSizeInBytes() : 1));
			
			this.context.put("maxAttachments", SystemGlobals.getValue(ConfigKeys.ATTACHMENTS_MAX_POST));
			this.context.put("forum", ForumRepository.getForum(p.getForumId()));
			this.context.put("action", "editSave");
			this.context.put("post", p);
			this.context.put("setType", p.getId() == topic.getFirstPostId());
			this.context.put("topic", topic);
			this.context.put("moduleAction", "post_form.htm");
			this.context.put("start", this.request.getParameter("start"));
			this.context.put("htmlAllowed", SecurityRepository.canAccess(SecurityConstants.PERM_HTML_DISABLED, 
					Integer.toString(topic.getForumId())));
			this.context.put("canCreateStickyOrAnnouncementTopics",
					SecurityRepository.canAccess(SecurityConstants.PERM_CREATE_STICKY_ANNOUNCEMENT_TOPICS));
		}
		else {
			this.context.put("moduleAction", "message.htm");
			this.context.put("message", I18n.getMessage("CannotEditPost"));
		}

		User u = PostCommon.getUserForDisplay(userId);

		if (preview) {
			u.setNotifyOnMessagesEnabled(this.request.getParameter("notify") != null);
			
			if (u.getId() != p.getUserId()) {
				// Probably a moderator is editing the message
				this.context.put("previewUser", PostCommon.getUserForDisplay(p.getUserId()));
			}
		}

		this.context.put("user", u);
	}
	
	public void quote() throws Exception {
		PostModel pm = DataAccessDriver.getInstance().newPostModel();
		Post p = pm.selectById(this.request.getIntParameter("post_id"));

		if (!this.anonymousPost(p.getForumId())) {
			return;
		}

		Topic t = DataAccessDriver.getInstance().newTopicModel().selectById(p.getTopicId());

		if (!TopicsCommon.isTopicAccessible(t.getForumId())) {
			return;
		}

		if (t.getStatus() == Topic.STATUS_LOCKED) {
			this.topicLocked();
			return;
		}

		if (p.getId() == 0) {
			this.postNotFound();
			return;
		}

		this.context.put("forum", ForumRepository.getForum(p.getForumId()));
		this.context.put("action", "insertSave");
		this.context.put("post", p);

		UserModel um = DataAccessDriver.getInstance().newUserModel();
		User u = um.selectById(p.getUserId());

		Topic topic = DataAccessDriver.getInstance().newTopicModel().selectById(p.getTopicId());
		int userId = SessionFacade.getUserSession().getUserId();
		
		this.context.put("attachmentsEnabled", SecurityRepository.canAccess(
				SecurityConstants.PERM_ATTACHMENTS_ENABLED, Integer.toString(topic.getForumId())));
		
		QuotaLimit ql = new AttachmentCommon(this.request).getQuotaLimit(userId);
		this.context.put("maxAttachmentsSize", new Long(ql != null ? ql.getSizeInBytes() : 1));
		
		this.context.put("maxAttachments", SystemGlobals.getValue(ConfigKeys.ATTACHMENTS_MAX_POST));
		this.context.put("isNewPost", true);
		this.context.put("topic", topic);
		this.context.put("quote", "true");
		this.context.put("quoteUser", u.getUsername());
		this.context.put("moduleAction", "post_form.htm");
		this.context.put("setType", false);
		this.context.put("htmlAllowed", SecurityRepository.canAccess(SecurityConstants.PERM_HTML_DISABLED, 
				Integer.toString(topic.getForumId())));
		this.context.put("start", this.request.getParameter("start"));
		this.context.put("user", DataAccessDriver.getInstance().newUserModel().selectById(userId));
	}

	public void editSave() throws Exception {
		PostModel pm = DataAccessDriver.getInstance().newPostModel();
		TopicModel tm = DataAccessDriver.getInstance().newTopicModel();

		Post p = pm.selectById(this.request.getIntParameter("post_id"));
		p = PostCommon.fillPostFromRequest(p, true);

		// The user wants to preview the message before posting it?
		if (this.request.getParameter("preview") != null) {
			this.context.put("preview", true);

			Post postPreview = new Post(p);
			this.context.put("postPreview", PostCommon.preparePostForDisplay(postPreview));

			this.edit(true, p);
		}
		else {
			Topic t = tm.selectById(p.getTopicId());

			if (!TopicsCommon.isTopicAccessible(t.getForumId())) {
				return;
			}

			if (t.getStatus() == Topic.STATUS_LOCKED
					&& !SecurityRepository.canAccess(SecurityConstants.PERM_MODERATION_POST_EDIT)) {
				this.topicLocked();
				return;
			}

			pm.update(p);
			
			// Attachments
			AttachmentCommon ac = new AttachmentCommon(this.request);
			ac.editAttachments(p.getId(), p.getForumId());
			
			try {
				ac.insertAttachments(p.getId(), p.getForumId());
			}
			catch (AttachmentException e) {
				JForum.enableCancelCommit();
				p.setText(this.request.getParameter("message"));
				this.context.put("errorMessage", e.getMessage());
				this.context.put("post", p);
				this.edit(false, p);
				return;
			}

			// Updates the topic title
			if (t.getFirstPostId() == p.getId()) {
				t.setTitle(p.getSubject());
				t.setType(this.request.getIntParameter("topic_type"));
				
				tm.update(t);
				
				ForumRepository.reloadForum(t.getForumId());
				TopicRepository.clearCache(t.getForumId());
			}

			if (this.request.getParameter("notify") == null) {
				tm.removeSubscription(p.getTopicId(), SessionFacade.getUserSession().getUserId());
			}

			String path = this.request.getContextPath() + "/posts/list/";
			String start = this.request.getParameter("start");
			if (start != null && !start.equals("0")) {
				path += start + "/";
			}

			path += p.getTopicId() + SystemGlobals.getValue(ConfigKeys.SERVLET_EXTENSION) + "#" + p.getId();
			JForum.setRedirect(path);
			
			if (SystemGlobals.getBoolValue(ConfigKeys.POSTS_CACHE_ENABLED)) {
				PostRepository.update(p.getTopicId(), PostCommon.preparePostForDisplay(p));
			}
		}
	}
	
	public void waitingModeration()
	{
		this.context.put("moduleAction", "message.htm");
		
		int topicId = this.request.getIntParameter("topic_id");
		String path = this.request.getContextPath();
		
		if (topicId == 0) {
			path += "/forums/show/" + this.request.getParameter("forum_id");
		}
		else {
			path += "/posts/list/" + topicId;
		}
		
		this.context.put("message", I18n.getMessage("PostShow.waitingModeration", 
				new String[] { path + SystemGlobals.getValue(ConfigKeys.SERVLET_EXTENSION) }));
	}
	
	private void notModeratedYet()
	{
		this.context.put("moduleAction", "message.htm");
		this.context.put("message", I18n.getMessage("PostShow.notModeratedYet"));
	}

	public void insertSave() throws Exception 
	{
		int forumId = this.request.getIntParameter("forum_id");
		boolean firstPost = false;

		if (!this.anonymousPost(forumId)) {
			SessionFacade.setAttribute(ConfigKeys.REQUEST_DUMP, this.request.dumpRequest());
			return;
		}
		
		Topic t = new Topic();
		t.setId(-1);
		t.setForumId(forumId);

		if (!TopicsCommon.isTopicAccessible(t.getForumId())
				|| this.isForumReadonly(t.getForumId(), this.request.getParameter("topic_id") != null)) {
			return;
		}

		TopicModel tm = DataAccessDriver.getInstance().newTopicModel();
		PostModel pm = DataAccessDriver.getInstance().newPostModel();
		ForumModel fm = DataAccessDriver.getInstance().newForumModel();

		if (this.request.getParameter("topic_id") != null) {
			t = tm.selectById(this.request.getIntParameter("topic_id"));

			// Cannot insert new messages on locked topics
			if (t.getStatus() == Topic.STATUS_LOCKED) {
				this.topicLocked();
				return;
			}
		}
		else {
			if (this.isReplyOnly(forumId)) {
				this.replyOnly();
				return;
			}
		}

		if (this.request.getParameter("topic_type") != null) {
			t.setType(this.request.getIntParameter("topic_type"));
		}

		UserSession us = SessionFacade.getUserSession();
		User u = new User();
		u.setId(us.getUserId());
		u.setUsername(us.getUsername());

		t.setPostedBy(u);

		// Set the Post
		Post p = PostCommon.fillPostFromRequest();
		p.setForumId(this.request.getIntParameter("forum_id"));
		
		if (p.getSubject() == null || p.getSubject() == "") {
			p.setSubject(t.getTitle());
		}

		boolean preview = (this.request.getParameter("preview") != null);
		boolean moderate = false;
		if (!preview) {
			// If topic_id is -1, then is the first post
			if (t.getId() == -1) {
				t.setTime(new Date());
				t.setTitle(this.request.getParameter("subject"));
				t.setModerated(ForumRepository.getForum(forumId).isModerated());

				t.setId(tm.addNew(t));
				firstPost = true;
			}
			
			// Moderators and admins don't need to have their messages moderated
			moderate = (t.isModerated() 
					&& !SecurityRepository.canAccess(SecurityConstants.PERM_MODERATION)
					&& !SecurityRepository.canAccess(SecurityConstants.PERM_ADMINISTRATION));

			// Topic watch
			if (this.request.getParameter("notify") != null) {
				this.watch(tm, t.getId(), u.getId());
			}

			p.setTopicId(t.getId());

			// Save the remaining stuff
			p.setModerate(moderate);
			int postId = pm.addNew(p);

			if (this.request.getParameter("topic_id") == null) {
				t.setFirstPostId(postId);
			}
			
			tm.update(t);
			
			// Attachments
			try {
				new AttachmentCommon(this.request).insertAttachments(postId, forumId);
			}
			catch (AttachmentException e) {
				JForum.enableCancelCommit();
				p.setText(this.request.getParameter("message"));
				p.setId(0);
				this.context.put("errorMessage", e.getMessage());
				this.context.put("post", p);
				this.insert();
				return;
			}

			if (!moderate) {
				DataAccessDriver.getInstance().newUserModel().incrementPosts(p.getUserId());
				TopicsCommon.updateBoardStatus(t, postId, firstPost, tm, fm);
				TopicsCommon.notifyUsers(t, tm);
	
				String path = this.request.getContextPath() + "/posts/list/";
				int start = ViewCommon.getStartPage();
	
				path += this.startPage(t, start) + "/";
				path += t.getId() + SystemGlobals.getValue(ConfigKeys.SERVLET_EXTENSION) + "#" + postId;
	
				JForum.setRedirect(path);
	
				int anonymousUser = SystemGlobals.getIntValue(ConfigKeys.ANONYMOUS_USER_ID);
				if (u.getId() != anonymousUser) {
					((Map) SessionFacade.getAttribute(ConfigKeys.TOPICS_TRACKING)).put(new Integer(t.getId()),
							new Long(p.getTime().getTime()));
				}
				
				if (SystemGlobals.getBoolValue(ConfigKeys.POSTS_CACHE_ENABLED)) {
					SimpleDateFormat df = new SimpleDateFormat(SystemGlobals.getValue(ConfigKeys.DATE_TIME_FORMAT));
					p.setFormatedTime(df.format(p.getTime()));
					
					PostRepository.append(p.getTopicId(), PostCommon.preparePostForDisplay(p));
				}
			}
			else {
				JForum.setRedirect(this.request.getContextPath() + "/posts/waitingModeration/" + (firstPost ? 0 : t.getId())
						+ "/" + t.getForumId()
						+ SystemGlobals.getValue(ConfigKeys.SERVLET_EXTENSION));
			}
		}
		else {
			this.context.put("preview", true);
			this.context.put("post", p);
			this.context.put("topic", t);
			this.context.put("start", this.request.getParameter("start"));

			Post postPreview = new Post(p);
			this.context.put("postPreview", PostCommon.preparePostForDisplay(postPreview));

			this.insert();
		}
	}

	private int startPage(Topic t, int currentStart) {
		int postsPerPage = SystemGlobals.getIntValue(ConfigKeys.POST_PER_PAGE);

		int newStart = (t.getTotalReplies() + 1) / postsPerPage * postsPerPage;
		if (newStart > currentStart) {
			return newStart;
		}
		
		return currentStart;
	}

	public void delete() throws Exception {
		if (!SecurityRepository.canAccess(SecurityConstants.PERM_MODERATION_POST_REMOVE)) {
			this.context.put("moduleAction", "message.htm");
			this.context.put("message", I18n.getMessage("CannotRemovePost"));

			return;
		}

		// Post
		PostModel pm = DataAccessDriver.getInstance().newPostModel();
		Post p = pm.selectById(this.request.getIntParameter("post_id"));

		TopicModel tm = DataAccessDriver.getInstance().newTopicModel();
		Topic t = tm.selectById(p.getTopicId());

		if (!TopicsCommon.isTopicAccessible(t.getForumId())) {
			return;
		}

		if (p.getId() == 0) {
			this.postNotFound();
			return;
		}

		pm.delete(p);
		DataAccessDriver.getInstance().newUserModel().decrementPosts(p.getUserId());
		
		// Attachments
		new AttachmentCommon(this.request).deleteAttachments(p.getId(), p.getForumId());

		// Topic
		tm.decrementTotalReplies(p.getTopicId());

		int maxPostId = tm.getMaxPostId(p.getTopicId());
		if (maxPostId > -1) {
			tm.setLastPostId(p.getTopicId(), maxPostId);
		}

		int minPostId = tm.getMinPostId(p.getTopicId());
		if (minPostId > -1) {
		  tm.setFirstPostId(p.getTopicId(), minPostId);
		}
        
		// Forum
		ForumModel fm = DataAccessDriver.getInstance().newForumModel();

		maxPostId = fm.getMaxPostId(p.getForumId());
		if (maxPostId > -1) {
			fm.setLastPost(p.getForumId(), maxPostId);
		}

		// It was the last remaining post in the topic?
		int totalPosts = tm.getTotalPosts(p.getTopicId());
		if (totalPosts > 0) {
			String page = this.request.getParameter("start");
			String returnPath = this.request.getContextPath() + "/posts/list/";

			if (page != null && !page.equals("") && !page.equals("0")) {
				int postsPerPage = SystemGlobals.getIntValue(ConfigKeys.POST_PER_PAGE);
				int newPage = Integer.parseInt(page);

				if (totalPosts % postsPerPage == 0) {
					newPage -= postsPerPage;
				}

				returnPath += newPage + "/";
			}

			JForum.setRedirect(returnPath + p.getTopicId() + SystemGlobals.getValue(ConfigKeys.SERVLET_EXTENSION));
		}
		else {
			// Ok, all posts were removed. Time to say goodbye
			TopicsCommon.deleteTopic(p.getTopicId(), p.getForumId(), false);

			JForum.setRedirect(this.request.getContextPath() + "/forums/show/" + p.getForumId()
					+ SystemGlobals.getValue(ConfigKeys.SERVLET_EXTENSION));
		}

		ForumRepository.reloadForum(p.getForumId());
		TopicRepository.clearCache(p.getForumId());
		PostRepository.clearCache(p.getTopicId());
	}

	private void watch(TopicModel tm, int topicId, int userId) throws Exception {
		if (!tm.isUserSubscribed(topicId, userId)) {
			tm.subscribeUser(topicId, userId);
		}
	}

	public void watch() throws Exception {
		int topicId = this.request.getIntParameter("topic_id");
		int userId = SessionFacade.getUserSession().getUserId();

		this.watch(DataAccessDriver.getInstance().newTopicModel(), topicId, userId);
		this.list();
	}

	public void unwatch() throws Exception {
		if (this.isUserLogged()) {
			int topicId = this.request.getIntParameter("topic_id");
			int userId = SessionFacade.getUserSession().getUserId();
			String start = this.request.getParameter("start");

			DataAccessDriver.getInstance().newTopicModel().removeSubscription(topicId, userId);

			String returnPath = this.request.getContextPath() + "/posts/list/";
			if (start != null && !start.equals("")) {
				returnPath += start + "/";
			}

			returnPath += topicId + SystemGlobals.getValue(ConfigKeys.SERVLET_EXTENSION);

			this.context.put("moduleAction", "message.htm");
			this.context.put("message", I18n.getMessage("ForumBase.unwatched", new String[] { returnPath }));
		}
		else {
			ViewCommon.contextToLogin();
		}
	}

	public void downloadAttach() throws Exception
	{
		if (!SecurityRepository.canAccess(SecurityConstants.PERM_ATTACHMENTS_DOWNLOAD)) {
			this.context.put("moduleAction", "message.htm");
			this.context.put("message", I18n.getMessage("Attachments.featureDisabled"));
			return;
		}
		
		int id = this.request.getIntParameter("attach_id");
		
		AttachmentModel am = DataAccessDriver.getInstance().newAttachmentModel();
		Attachment a = am.selectAttachmentById(id);
		
		String filename = SystemGlobals.getValue(ConfigKeys.ATTACHMENTS_STORE_DIR)
			+ "/"
			+ a.getInfo().getPhysicalFilename();
		
		if (!new File(filename).exists()) {
			this.context.put("moduleAction", "message.htm");
			this.context.put("message", I18n.getMessage("Attachments.notFound"));
			return;
		}
		
		a.getInfo().setDownloadCount(a.getInfo().getDownloadCount() + 1);
		am.updateAttachment(a);
		
		FileInputStream fis = new FileInputStream(filename);
		OutputStream os = response.getOutputStream();

		this.response.setContentType("application/octet-stream");
		this.response.setHeader("Content-Disposition", "attachment; filename=\"" + a.getInfo().getRealFilename() + "\";");
		this.response.setContentLength((int)a.getInfo().getFilesize());
		
		byte[] b = new byte[4096];
		int c = 0;
		while ((c = fis.read(b)) != -1) {
			os.write(b);
		}
		
		fis.close();
		os.close();
		
		JForum.enableBinaryContent(true);
	}
	
	private boolean isUserLogged() {
		return (SessionFacade.getAttribute("logged") != null && SessionFacade.getAttribute("logged").equals("1"));
	}

	private void topicLocked() {
		this.context.put("moduleAction", "message.htm");
		this.context.put("message", I18n.getMessage("PostShow.topicLocked"));
	}
	
	public void listSmilies()
	{
		this.setTemplateName(SystemGlobals.getValue(ConfigKeys.TEMPLATE_NAME) + "/empty.htm");
		this.context.put("moduleAction", "list_smilies.htm");
		this.context.put("smilies", SmiliesRepository.getSmilies());
	}

	private boolean isForumReadonly(int forumId, boolean isReply) throws Exception {
		if (!SecurityRepository.canAccess(SecurityConstants.PERM_READ_ONLY_FORUMS, Integer.toString(forumId))) {
			if (isReply) {
				this.list();
			}
			else {
				JForum.setRedirect(this.request.getContextPath() + "/forums/show/" + forumId
						+ SystemGlobals.getValue(ConfigKeys.SERVLET_EXTENSION));
			}

			return true;
		}

		return false;
	}

	private boolean anonymousPost(int forumId) throws Exception {
		// Check if anonymous posts are allowed
		if (!this.isUserLogged()
				&& !SecurityRepository.canAccess(SecurityConstants.PERM_ANONYMOUS_POST, Integer.toString(forumId))) {
			ViewCommon.contextToLogin();

			return false;
		}

		return true;
	}
}