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
 * This file creation date: 10/03/2004 - 18:43:12
 * The JForum Project
 * http://www.jforum.net
 */
package net.jforum.view.forum;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import net.jforum.JForum;
import net.jforum.entities.Topic;
import net.jforum.model.DataAccessDriver;
import net.jforum.model.ForumModel;
import net.jforum.model.TopicModel;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.PostRepository;
import net.jforum.repository.SecurityRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.security.SecurityConstants;
import net.jforum.util.I18n;
import net.jforum.view.forum.common.ForumCommon;

/**
 * @author Rafael Steil
 * @version $Id: ModerationHelper.java,v 1.14.6.1 2005/03/28 15:59:52 rafaelsteil Exp $
 */
public class ModerationHelper 
{
	private static final Logger logger = Logger.getLogger(ModerationHelper.class);
	
	public static final int SUCCESS = 1;
	public static final int FAILURE = 2;
	public static final int IGNORE = 3;
	
	public int doModeration(String successUrl) throws Exception
	{
		int status = FAILURE;

		if (SecurityRepository.canAccess(SecurityConstants.PERM_MODERATION)) {
			// Deleting topics
			if (JForum.getRequest().getParameter("topicRemove") != null) {
				if (SecurityRepository.canAccess(SecurityConstants.PERM_MODERATION_POST_REMOVE)) {
					this.removeTopics();
					
					status = SUCCESS;
				}
			}
			else if (JForum.getRequest().getParameter("topicMove") != null) {
				if (SecurityRepository.canAccess(SecurityConstants.PERM_MODERATION_TOPIC_MOVE)) {
					this.moveTopics();
					
					status = IGNORE;
				}
			}
			else if (JForum.getRequest().getParameter("topicLock") != null) {
				if (SecurityRepository.canAccess(SecurityConstants.PERM_MODERATION_TOPIC_LOCK_UNLOCK)) {
					this.lockUnlockTopics(Topic.STATUS_LOCKED);
					
					status = SUCCESS;
				}
			}
			else if (JForum.getRequest().getParameter("topicUnlock") != null) {
				if (SecurityRepository.canAccess(SecurityConstants.PERM_MODERATION_TOPIC_LOCK_UNLOCK)) {
					this.lockUnlockTopics(Topic.STATUS_UNLOCKED);
					
					status = SUCCESS;
				}
			}
		}

		if (status == ModerationHelper.FAILURE) {
			this.denied();
		}
		else if (status == ModerationHelper.SUCCESS && successUrl != null) {
			JForum.setRedirect(successUrl);
		}
		
		return status;
	}
	
	public int doModeration() throws Exception
	{
		return this.doModeration(null);
	}
	
	private void removeTopics() throws Exception
	{
		String[] topics = JForum.getRequest().getParameterValues("topic_id");
		
		List forumsList = new ArrayList();
		TopicModel tm = DataAccessDriver.getInstance().newTopicModel();
		
		List topicsToDelete = new ArrayList();
		
		if (topics != null && topics.length > 0) {
			for (int i = 0; i < topics.length; i++) {
				Topic t = tm.selectById(Integer.parseInt(topics[i]));
				
				if (!forumsList.contains(new Integer(t.getForumId()))) {
					forumsList.add(new Integer(t.getForumId()));
				}
				
				topicsToDelete.add(t);
				PostRepository.clearCache(t.getId());
			}
			
			tm.deleteTopics(topicsToDelete);
			
			ForumModel fm = DataAccessDriver.getInstance().newForumModel();
			TopicRepository.loadMostRecentTopics();
			
			// Reload changed forums
			for (Iterator iter = forumsList.iterator(); iter.hasNext(); ) {
				int forumId = ((Integer)iter.next()).intValue();

				TopicRepository.clearCache(forumId);
				
				int postId = fm.getMaxPostId(forumId);
				if (postId > -1) {
					fm.setLastPost(forumId, postId);
				}
				else {
					logger.warn("Could not find last post id for forum " + forumId);
				}
				
				ForumRepository.reloadForum(forumId);
			}
		}
	}
	
	private void lockUnlockTopics(int status) throws Exception
	{
		String[] topics = JForum.getRequest().getParameterValues("topic_id");
		
		if (topics != null && topics.length > 0) {
			int[] ids = new int[topics.length];

			for (int i = 0; i < topics.length; i++) {
				ids[i] = Integer.parseInt(topics[i]);
			}
			
			DataAccessDriver.getInstance().newTopicModel().lockUnlock(ids, status);
			
			// Clear the cache
			Topic t = DataAccessDriver.getInstance().newTopicModel().selectById(ids[0]);
			TopicRepository.clearCache(t.getForumId());
		}
	}
	
	private void moveTopics() throws Exception
	{
		JForum.getContext().put("moduleAction", "post_move.htm");
		JForum.getContext().put("persistData", JForum.getRequest().getParameter("persistData"));
		JForum.getContext().put("allCategories", ForumCommon.getAllCategoriesAndForums(false));
		
		String[] topics = JForum.getRequest().getParameterValues("topic_id");
		if (topics.length > 0) {
			// If forum_id is null, get from the database
			String forumId = JForum.getRequest().getParameter("forum_id");
			if (forumId == null) {
				forumId = Integer.toString(DataAccessDriver.getInstance().newTopicModel().selectById(
						Integer.parseInt(topics[0])).getForumId());
			}
			
			JForum.getContext().put("forum_id", forumId);
			
			StringBuffer sb = new StringBuffer(128);
			for (int i = 0; i < topics.length - 1; i++) {
				sb.append(topics[i]).append(",");
			}
			
			sb.append(topics[topics.length - 1]);
			
			JForum.getContext().put("topics", sb.toString());
		}
	}
	
	public int moveTopicsSave(String successUrl) throws Exception
	{
		int status = SUCCESS;
		if (!SecurityRepository.canAccess(SecurityConstants.PERM_MODERATION_TOPIC_MOVE)) {
			status = FAILURE;
		}
		else {
			String topics = JForum.getRequest().getParameter("topics");
			if (topics != null) {
				int fromForumId = Integer.parseInt(JForum.getRequest().getParameter("forum_id"));
				int toForumId = Integer.parseInt(JForum.getRequest().getParameter("to_forum"));
				
				DataAccessDriver.getInstance().newForumModel().moveTopics(topics.split(","), fromForumId, toForumId);
				
				ForumRepository.reloadForum(fromForumId);
				ForumRepository.reloadForum(toForumId);
				
				TopicRepository.clearCache(fromForumId);
				TopicRepository.clearCache(toForumId);
				
				TopicRepository.loadMostRecentTopics();
			}
		}
		
		if (status == FAILURE) {
			this.denied();
		}
		else {
			this.moderationDone(successUrl);
		}
		
		return status;
	}
	
	public void moderationDone(String redirectUrl)
	{
		JForum.getContext().put("moduleAction", "message.htm");
		JForum.getContext().put("message", I18n.getMessage("Moderation.ModerationDone", new String[] { redirectUrl }));
	}
	
	public void denied()
	{
		this.denied(I18n.getMessage("Moderation.Denied"));
	}
	
	public void denied(String message)
	{
		JForum.getContext().put("moduleAction", "message.htm");
		JForum.getContext().put("message", message);		
	}
}
