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
 * This file creation date: Apr 6, 2003 / 2:38:28 PM
 * The JForum Project
 * http://www.jforum.net
 */
package net.jforum.drivers.generic;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;

import net.jforum.JForum;
import net.jforum.SessionFacade;
import net.jforum.entities.Topic;
import net.jforum.entities.User;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

/**
 * @author Rafael Steil
 * @version $Id: TopicModel.java,v 1.5 2004/06/05 22:09:58 rafaelsteil Exp $
 */
public class TopicModel extends AutoKeys implements net.jforum.model.TopicModel 
{
	private int DUPLICATE_KEY = 1062;

	/** 
	 * @see net.jforum.model.TopicModel#selectById(int)
	 */
	public Topic selectById(int topicId) throws Exception 
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.selectById"));
		p.setInt(1, topicId);
		
		Topic t = new Topic();
		ResultSet rs = p.executeQuery();
		ArrayList l = this.fillTopicsData(rs);
		if (l.size() > 0) {
			t = (Topic)l.get(0);
		}
		
		rs.close();
		p.close();
		return t;
	}

	/** 
	 * @see net.jforum.model.TopicModel#delete(int)
	 */
	public void delete(Topic topic) throws Exception 
	{
		// Topic
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.delete"));
		p.setInt(1, topic.getId());
		p.executeUpdate();
		
		// Related posts
		p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.deletePosts"));
		p.setInt(1, topic.getId());
		p.executeUpdate();
		
		// Update forum stats
		ForumModel fm = new ForumModel();
		fm.decrementTotalTopics(topic.getForumId(), 1);
		
		p.close();
	}

	/** 
	 * @see net.jforum.model.TopicModel#update(net.jforum.Topic)
	 */
	public void update(Topic topic) throws Exception 
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.update"));
		
		p.setString(1, topic.getTitle());
		p.setInt(2, topic.getLastPostId());
		p.setInt(3, topic.getFirstPostId());
		p.setInt(4, topic.getType());
		p.setInt(5, topic.getId());			
		p.executeUpdate();
		
		p.close();
	}

	/** 
	 * @see net.jforum.model.TopicModel#addNew(net.jforum.Topic)
	 */
	public int addNew(Topic topic) throws Exception 
	{
		PreparedStatement p = this.getStatementForAutoKeys("TopicModel.addNew");
		
		p.setInt(1, topic.getForumId());
		p.setString(2, topic.getTitle());
		p.setInt(3, topic.getPostedBy().getId());
		p.setLong(4, topic.getTime());
		p.setInt(5, topic.getFirstPostId());
		p.setInt(6, topic.getLastPostId());
		p.setInt(7, topic.getType());
		
		int topicId = this.executeAutoKeysQuery(p);
			
		p.close();
		return topicId;
	}

	/** 
	 * @see net.jforum.model.TopicModel#incrementTotalViews(int)
	 */
	public void incrementTotalViews(int topicId) throws Exception 
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.incrementTotalViews"));
		p.setInt(1, topicId);
		p.executeUpdate();
		p.close();
	}

	/** 
	 * @see net.jforum.model.TopicModel#incrementTotalReplies(int)
	 */
	public void incrementTotalReplies(int topicId) throws Exception 
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.incrementTotalReplies"));
		p.setInt(1, topicId);
		p.executeUpdate();
		p.close();
	}

	/** 
	 * @see net.jforum.model.TopicModel#decrementTotalReplies(int)
	 */
	public void decrementTotalReplies(int topicId) throws Exception 
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.decrementTotalReplies"));
		p.setInt(1, topicId);
		p.executeUpdate();
		p.close();
	}

	/** 
	 * @see net.jforum.model.TopicModel#setLastPostId(int, int)
	 */
	public void setLastPostId(int topicId, int postId) throws Exception 
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.setLastPostId"));
		p.setInt(1, postId);
		p.setInt(2, topicId);
		p.executeUpdate();
		p.close();
	}

	/** 
	 * @see net.jforum.model.TopicModel#selectAllByForum(int)
	 */
	public ArrayList selectAllByForum(int forumId) throws Exception 
	{
		return this.selectAllByForumByLimit(forumId, 0, Integer.MAX_VALUE);
	}
	
	/** 
	 * @see net.jforum.model.TopicModel#selectAllByForumByLimit(int, int, int)
	 */
	public ArrayList selectAllByForumByLimit(
		int forumId,
		int startFrom,
		int count)
		throws Exception 
	{
		ArrayList l = new ArrayList();

		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.selectAllByForumByLimit"));
		p.setInt(1, forumId);
		p.setInt(2, startFrom);
		p.setInt(3, count);
		
		ResultSet rs = p.executeQuery();
		
		l = this.fillTopicsData(rs);
		
		rs.close();
		return l;
	}
	
	public ArrayList fillTopicsData(ResultSet rs) throws SQLException
	{
		SimpleDateFormat df = new SimpleDateFormat(SystemGlobals.getValue(ConfigKeys.DATE_TIME_FORMAT));
		ArrayList l = new ArrayList();
		
		while (rs.next()) {
			Topic t = new Topic();
			
			t.setTitle(rs.getString("topic_title"));
			t.setId(rs.getInt("topic_id"));
			t.setTime(rs.getLong("topic_time"));
			t.setStatus(rs.getInt("topic_status"));
			t.setTotalViews(rs.getInt("topic_views"));
			t.setTotalReplies(rs.getInt("topic_replies"));
			t.setFirstPostId(rs.getInt("topic_first_post_id"));
			t.setLastPostId(rs.getInt("topic_last_post_id"));
			t.setType(rs.getInt("topic_type"));
			t.setForumId(rs.getInt("forum_id"));

			// First Post Time
			GregorianCalendar gc = new GregorianCalendar();
			gc.setTimeInMillis(rs.getLong("topic_time"));
			t.setFirstPostTime(df.format(gc.getTime()));
			
			// Last Post Time
			gc.setTimeInMillis(rs.getLong("post_time"));
			t.setLastPostTime(df.format(gc.getTime()));
			t.setLastPostTimeInMillis(rs.getLong("post_time"));

			// Created by
			User u = new User();
			u.setId(rs.getInt("posted_by_id"));
			u.setUsername(rs.getString("posted_by_username"));

			t.setPostedBy(u);
			
			// Last post by
			u = new User();
			u.setId(rs.getInt("last_post_by_id"));
			u.setUsername(rs.getString("last_post_by_username"));
			
			t.setLastPostBy(u);
			
			l.add(t);
		}
		
		return l;
	}

	/** 
	 * @see net.jforum.model.TopicModel#autoSetLastPostId(int)
	 */
	public int getMaxPostId(int topicId) throws Exception 
	{
		int id = -1;
		
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.getMaxPostId"));
		p.setInt(1, topicId);
		
		ResultSet rs = p.executeQuery();
		if (rs.next()) {
			id = rs.getInt("post_id");
		}
		
		rs.close();
		p.close();
		
		return id;
	}

	/** 
	 * @see net.jforum.model.TopicModel#getTotalPosts(int)
	 */
	public int getTotalPosts(int topicId) throws Exception 
	{
		int total = 0;
		
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.getTotalPosts"));
		p.setInt(1, topicId);
		
		ResultSet rs = p.executeQuery();
		if (rs.next()) {
			total = rs.getInt("total");
		}
		
		rs.close();
		p.close();
		
		return total;
	}

	/** 
	 * @see net.jforum.model.TopicModel#selectLastN(int)
	 */
	public ArrayList selectLastN(int count) throws Exception 
	{
		ArrayList topics = new ArrayList();
		
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.selectLastN"));
		p.setInt(1, count);
		
		ResultSet rs = p.executeQuery();
		
		// If you want more fields here, just put the code. At the time
		// this code was written, these were the only needed fields ;)
		while (rs.next()) {
			Topic t = new Topic();
			
			t.setTitle(rs.getString("topic_title"));
			t.setId(rs.getInt("topic_id"));
			t.setTime(rs.getLong("topic_time"));
			t.setType(rs.getInt("topic_type"));
			
			topics.add(t);
		}
		
		rs.close();
		p.close();
		
		return topics;
	}
	
	/**
 	 * @see net.jforum.model.TopicModel#notifyUsers(int)
 	 */
	public ArrayList notifyUsers(Topic topic) throws Exception 
	{ 
		int posterId = SessionFacade.getUserSession().getUserId();
		int anonUser = SystemGlobals.getIntValue(ConfigKeys.ANONYMOUS_USER_ID);
		
		PreparedStatement stmt = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.notifyUsers"));		
		ResultSet rs = null;

		stmt.setInt(1, topic.getId());
		stmt.setInt(2, posterId); //don't notify the poster
		stmt.setInt(3, anonUser); //don't notify the anonimous user
				
		rs = stmt.executeQuery();
		
		ArrayList users = new ArrayList();
		while(rs.next()) {
			User user = new User();

			user.setId(rs.getInt("user_id"));
			user.setEmail(rs.getString("user_email"));
			user.setUsername(rs.getString("username"));
			user.setLang(rs.getString("user_lang"));
			
			users.add(user);
		}
		
		// Set read status to false
		stmt = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.markAllAsUnread"));
		stmt.setInt(1, topic.getId());
		stmt.setInt(2, posterId); //don't notify the poster
		stmt.setInt(3, anonUser); //don't notify the anonimous user
		
		stmt.executeUpdate();
			
		rs.close();
		stmt.close();
		
		return users;
	}
	
	/**
	 * @see net.jforum.model.TopicModel#subscribeUser(int, int)
	 */
	public void subscribeUser(int topicId, int userId) throws Exception 
	{
		PreparedStatement stmt = JForum.getConnection(). prepareStatement( SystemGlobals.getSql("TopicModel.subscribeUser"));
		
		try {
			stmt.setInt(1, topicId);
			stmt.setInt(2, userId);
			
			stmt.executeUpdate();
		} catch(SQLException e) {
			// Ignore duplicate key warnings
			if(e.getErrorCode() != DUPLICATE_KEY) {
				throw e;			
			}
		}
		finally {
			if(stmt != null) {
				stmt.close();
			}
		}		
	}
	
	/**
	 * @see net.jforum.model.TopicModel#isUserSubscribing(int, int)
	 */
	public boolean isUserSubscribed(int topicId, int userId) throws Exception 
	{
		PreparedStatement stmt = JForum.getConnection(). prepareStatement( SystemGlobals.getSql("TopicModel.isUserSubscribed"));
		ResultSet rs = null;
		
		stmt.setInt(1, topicId);
		stmt.setInt(2, userId);
		
		rs = stmt.executeQuery();
		boolean status = rs.next();
		
		rs.close();
		stmt.close();
				
		return status;
	}
	
	/** 
	 * @see net.jforum.model.TopicModel#removeSubscription(int, int)
	 */
	public void removeSubscription(int topicId, int userId) throws Exception 
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.removeSubscription"));
		p.setInt(1, topicId);
		p.setInt(2, userId);
		
		p.executeUpdate();
		p.close();
	}
	
	/** 
	 * @see net.jforum.model.TopicModel#removeSubscriptionByTopic(int)
	 */
	public void removeSubscriptionByTopic(int topicId) throws Exception 
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.removeSubscriptionByTopic"));
		p.setInt(1, topicId);
		
		p.executeUpdate();
		p.close();
	}

	/** 
	 * @see net.jforum.model.TopicModel#updateReadStatus(int, int, boolean)
	 */
	public void updateReadStatus(int topicId, int userId, boolean read) throws Exception 
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.updateReadStatus"));
		p.setInt(1, read ? 1 : 0);
		p.setInt(2, topicId);
		p.setInt(3, userId);
		
		p.executeUpdate();
		p.close();
	}
	
	/** 
	 * @see net.jforum.model.TopicModel#lockUnlock(int, int)
	 */
	public void lockUnlock(int[] topicId, int status) throws Exception 
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.lockUnlock"));
		p.setInt(1, status);
		
		for (int i = 0; i < topicId.length; i++) {
			p.setInt(2, topicId[i]);
			p.executeUpdate();
		}
		p.close();
	}
}