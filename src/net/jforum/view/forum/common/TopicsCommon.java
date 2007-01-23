/*
 * Copyright (c) JForum Team
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
 * Created on 17/10/2004 23:54:47
 * The JForum Project
 * http://www.jforum.net
 */
package net.jforum.view.forum.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.jforum.JForumExecutionContext;
import net.jforum.SessionFacade;
import net.jforum.dao.DataAccessDriver;
import net.jforum.dao.ForumDAO;
import net.jforum.dao.TopicDAO;
import net.jforum.entities.Forum;
import net.jforum.entities.Post;
import net.jforum.entities.Topic;
import net.jforum.entities.UserSession;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.SecurityRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.security.PermissionControl;
import net.jforum.security.SecurityConstants;
import net.jforum.util.I18n;
import net.jforum.util.concurrent.executor.QueuedExecutor;
import net.jforum.util.mail.EmailSenderTask;
import net.jforum.util.mail.TopicReplySpammer;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;
import net.jforum.view.forum.ModerationHelper;

import org.apache.log4j.Logger;

import freemarker.template.SimpleHash;

/**
 * General utilities methods for topic manipulation.
 * 
 * @author Rafael Steil
 * @version $Id: TopicsCommon.java,v 1.34.4.3 2007/01/23 20:57:12 lazee Exp $
 */
public class TopicsCommon 
{
	private static Logger logger = Logger.getLogger(TopicsCommon.class);
	
	/**
	 * List all first 'n' topics of a given forum.
	 * This method returns no more than <code>ConfigKeys.TOPICS_PER_PAGE</code>
	 * topics for the forum. 
	 * 
	 * @param forumId The forum id to which the topics belongs to
	 * @param start The start fetching index
	 * @return <code>java.util.List</code> containing the topics found.
	 */
	public static List topicsByForum(int forumId, int start)
	{
		TopicDAO tm = DataAccessDriver.getInstance().newTopicDAO();
		int topicsPerPage = SystemGlobals.getIntValue(ConfigKeys.TOPICS_PER_PAGE);
		List topics;
		
		// Try to get the first's page of topics from the cache
		if (start == 0 && SystemGlobals.getBoolValue(ConfigKeys.TOPIC_CACHE_ENABLED)) {
			topics = TopicRepository.getTopics(forumId);

			if (topics.size() == 0 || !TopicRepository.isLoaded(forumId)) {
				topics = tm.selectAllByForumByLimit(forumId, start, topicsPerPage);
				TopicRepository.addAll(forumId, topics);
			}
		}
		else {
			topics = tm.selectAllByForumByLimit(forumId, start, topicsPerPage);
		}
		
		return topics;
	}
	
	/**
	 * Prepare the topics for listing.
	 * This method does some preparation for a set ot <code>net.jforum.entities.Topic</code>
	 * instances for the current user, like verification if the user already
	 * read the topic, if pagination is a need and so on.
	 * 
	 * @param topics The topics to process
	 * @return The post-processed topics.
	 */
	public static List prepareTopics(List topics)
	{
		UserSession userSession = SessionFacade.getUserSession();

		long lastVisit = userSession.getLastVisit().getTime();
		int hotBegin = SystemGlobals.getIntValue(ConfigKeys.HOT_TOPIC_BEGIN);

		int postsPerPage = SystemGlobals.getIntValue(ConfigKeys.POST_PER_PAGE);
		Map topicsTracking = (HashMap)SessionFacade.getAttribute(ConfigKeys.TOPICS_TRACKING);
		List newTopics = new ArrayList(topics.size());
		
		boolean checkUnread = (userSession.getUserId() 
			!= SystemGlobals.getIntValue(ConfigKeys.ANONYMOUS_USER_ID));
		
		for (Iterator iter = topics.iterator(); iter.hasNext(); ) {
			boolean read = false;
			Topic t = (Topic)iter.next();

			if (checkUnread && t.getLastPostDate().getTime() > lastVisit) {
				if (topicsTracking.containsKey(new Integer(t.getId()))) {
					read = (((Long)topicsTracking.get(new Integer(t.getId()))).longValue() > t.getLastPostDate().getTime());
				}
			}
			else {
				read = true;
			}
					
			
			if (t.getTotalReplies() + 1 > postsPerPage) {
				t.setPaginate(true);
				t.setTotalPages(new Double(Math.floor(t.getTotalReplies() / postsPerPage)));
			}
			else {
				t.setPaginate(false);
				t.setTotalPages(new Double(0));
			}
			
			// Check if this is a hot topic
			t.setHot(t.getTotalReplies() >= hotBegin);
			
			t.setRead(read);
			newTopics.add(t);
		}
		
		return newTopics;
	}

	/**
	 * Common properties to be used when showing topic data
	 */
	public static void topicListingBase()
	{
		SimpleHash context = JForumExecutionContext.getTemplateContext();
		
		// Topic Types
		context.put("TOPIC_ANNOUNCE", new Integer(Topic.TYPE_ANNOUNCE));
		context.put("TOPIC_STICKY", new Integer(Topic.TYPE_STICKY));
		context.put("TOPIC_NORMAL", new Integer(Topic.TYPE_NORMAL));
	
		// Topic Status
		context.put("STATUS_LOCKED", new Integer(Topic.STATUS_LOCKED));
		context.put("STATUS_UNLOCKED", new Integer(Topic.STATUS_UNLOCKED));
		
		// Moderation
		PermissionControl pc = SecurityRepository.get(SessionFacade.getUserSession().getUserId());
		
		context.put("moderator", pc.canAccess(SecurityConstants.PERM_MODERATION));
		context.put("can_remove_posts", pc.canAccess(SecurityConstants.PERM_MODERATION_POST_REMOVE));
		context.put("can_move_topics", pc.canAccess(SecurityConstants.PERM_MODERATION_TOPIC_MOVE));
		context.put("can_lockUnlock_topics", pc.canAccess(SecurityConstants.PERM_MODERATION_TOPIC_LOCK_UNLOCK));
		context.put("rssEnabled", SystemGlobals.getBoolValue(ConfigKeys.RSS_ENABLED));
	}
	
	/**
	 * Checks if the user is allowed to view the topic.
	 * If there currently logged user does not have access
	 * to the forum, the template context will be set to show
	 * an error message to the user, by calling
	 * <blockquote>new ModerationHelper().denied(I18n.getMessage("PostShow.denied"))</blockquote>
	 * @param forumId The forum id to which the topics belongs to
	 * @return <code>true</code> if the topic is accessible, <code>false</code> otherwise
	 */
	public static boolean isTopicAccessible(int forumId)
	{
		Forum f = ForumRepository.getForum(forumId);
		
		if (f == null || !ForumRepository.isCategoryAccessible(f.getCategoryId())) {
			new ModerationHelper().denied(I18n.getMessage("PostShow.denied"));
			return false;
		}

		return true;
	}
	
	/**
	 * Sends a "new post" notification message to all users watching the topic.
	 * 
	 * @param t The changed topic
	 * @param p The new message
	 */
	public static void notifyUsers(Topic t, Post p)
	{
		if (SystemGlobals.getBoolValue(ConfigKeys.MAIL_NOTIFY_ANSWERS)) {
			try {
				TopicDAO dao = DataAccessDriver.getInstance().newTopicDAO();
				List usersToNotify = dao.notifyUsers(t);

				// we only have to send an email if there are users
				// subscribed to the topic
				if (usersToNotify != null && usersToNotify.size() > 0) {
					QueuedExecutor.getInstance().execute(
						new EmailSenderTask(new TopicReplySpammer(t, p, usersToNotify)));
				}
			}
			catch (Exception e) {
				logger.warn("Error while sending notification emails: " + e);
			}
		}
	}
	
	/**
	 * Updates the board status after a new post is inserted.
	 * This method is used in conjunct with moderation manipulation. 
	 * It will increase by 1 the number of replies of the tpoic, set the
	 * last post id for the topic and the forum and refresh the cache. 
	 * 
	 * @param t Topic The topic to update
	 * @param lastPostId int The id of the last post
	 * @param tm TopicDAO A TopicModel instance
	 * @param fm ForumDAO A ForumModel instance
     * @param firstPost boolean
	 */
	public static void updateBoardStatus(Topic t, int lastPostId, boolean firstPost, TopicDAO tm, ForumDAO fm)
	{
		t.setLastPostId(lastPostId);
		tm.update(t);
		
		fm.setLastPost(t.getForumId(), lastPostId);
		
		if (!firstPost) {
			tm.incrementTotalReplies(t.getId());
		}
		else {
			fm.incrementTotalTopics(t.getForumId(), 1);
		}
		
		tm.incrementTotalViews(t.getId());
		
		TopicRepository.addTopic(t);
		TopicRepository.pushTopic(t);
		ForumRepository.incrementTotalMessages();
	}
	
	/**
	 * Deletes a topic.
	 * This method will remove the topic from the database,
	 * clear the entry frm the cache and update the last 
	 * post info for the associated forum.
	 * @param topicId The topic id to remove
	 * @param fromModeration boolean TODO
     * @param forumId int
	 */
	public static void deleteTopic(int topicId, int forumId, boolean fromModeration)
	{
		TopicDAO tm = DataAccessDriver.getInstance().newTopicDAO();
        //TODO use or delete
        //ForumDAO fm = DataAccessDriver.getInstance().newForumDAO();
		
		Topic topic = new Topic();
		topic.setId(topicId);
		topic.setForumId(forumId);

		tm.delete(topic);

		if (!fromModeration) {
			// Updates the Recent Topics if it contains this topic
			TopicRepository.loadMostRecentTopics();
			TopicRepository.clearCache(forumId);
			tm.removeSubscriptionByTopic(topicId);
		}
	}
}
