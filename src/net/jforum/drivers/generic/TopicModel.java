/*
 * Copyright (c) 2003, 2004 Rafael Steil
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
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.jforum.JForum;
import net.jforum.SessionFacade;
import net.jforum.entities.Topic;
import net.jforum.entities.User;
import net.jforum.model.DataAccessDriver;
import net.jforum.model.ForumModel;
import net.jforum.model.PostModel;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

/**
 * @author Rafael Steil
 * @version $Id: TopicModel.java,v 1.15.6.1 2005/03/28 15:59:46 rafaelsteil Exp $
 */
public class TopicModel extends AutoKeys implements net.jforum.model.TopicModel 
{
	private int DUPLICATE_KEY = 1062;
	
	/**
	 * @see net.jforum.model.TopicModel#fixFirstLastPostId(int)
	 */
	public void fixFirstLastPostId(int topicId) throws Exception
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.getFirstLastPostId"));
		p.setInt(1, topicId);
		
		ResultSet rs = p.executeQuery();
		if (rs.next()) {
			int first = rs.getInt("first_post_id");
			int last = rs.getInt("last_post_id");
			
			rs.close();
			p.close();
			
			p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.fixFirstLastPostId"));
			p.setInt(1, first);
			p.setInt(2, last);
			p.setInt(3, topicId);
			p.executeUpdate();
		}
		
		rs.close();
		p.close();
	}

	/** 
	 * @see net.jforum.model.TopicModel#selectById(int)
	 */
	public Topic selectById(int topicId) throws Exception 
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.selectById"));
		p.setInt(1, topicId);
		
		Topic t = new Topic();
		ResultSet rs = p.executeQuery();
		List l = this.fillTopicsData(rs);
		if (l.size() > 0) {
			t = (Topic)l.get(0);
		}
		
		rs.close();
		p.close();
		return t;
	}
	
	/**
	 * @see net.jforum.model.TopicModel#selectRaw(int)
	 */
	public Topic selectRaw(int topicId) throws Exception
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.selectRaw"));
		p.setInt(1, topicId);
		
		Topic t = new Topic();
		ResultSet rs = p.executeQuery();
		if (rs.next()) {
			t = this.getBaseTopicData(rs);
		}
		
		rs.close();
		p.close();
		return t;
	}

	/** 
	 * @see net.jforum.model.TopicModel#delete(int)
	 */
	public void delete(final Topic topic) throws Exception 
	{
		this.deleteTopics(new ArrayList() {{ add(topic); }});
	}
	
	public void deleteTopics(List topics) throws Exception
	{
		// Topic
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.delete"));
		ForumModel fm = DataAccessDriver.getInstance().newForumModel();
		
		PostModel pm = DataAccessDriver.getInstance().newPostModel();
		
		for (Iterator iter = topics.iterator(); iter.hasNext(); ) {
			Topic topic = (Topic)iter.next();

			// Remove watches
			this.removeSubscriptionByTopic(topic.getId());

			// Remove the messages
			pm.deleteByTopic(topic.getId());
			
			p.setInt(1, topic.getId());
			p.executeUpdate();
			
			// Update forum stats
			fm.decrementTotalTopics(topic.getForumId(), 1);
		}
		
		p.close();
	}
	
	/** 
	 * @see net.jforum.model.TopicModel#deleteByForum(int)
	 */
	public void deleteByForum(int forumId) throws Exception 
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.deleteByForum"));
		p.setInt(1, forumId);
		
		ResultSet rs = p.executeQuery();
		List topics = new ArrayList();
		while (rs.next()) {
			Topic t = new Topic();
			t.setId(rs.getInt("topic_id"));
			
			topics.add(t);
		}
		
		rs.close();
		p.close();
		
		this.deleteTopics(topics);
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
		p.setInt(5, topic.isModerated() ? 1 : 0);
		p.setInt(6, topic.getId());
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
		p.setTimestamp(4, new Timestamp(topic.getTime().getTime()));
		p.setInt(5, topic.getFirstPostId());
		p.setInt(6, topic.getLastPostId());
		p.setInt(7, topic.getType());
		p.setInt(8, topic.isModerated() ? 1 : 0);
		
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
	public List selectAllByForum(int forumId) throws Exception 
	{
		return this.selectAllByForumByLimit(forumId, 0, Integer.MAX_VALUE);
	}
	
	/** 
	 * @see net.jforum.model.TopicModel#selectAllByForumByLimit(int, int, int)
	 */
	public List selectAllByForumByLimit(
		int forumId,
		int startFrom,
		int count)
		throws Exception 
	{
		List l = new ArrayList();

		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.selectAllByForumByLimit"));
		p.setInt(1, forumId);
		p.setInt(2, startFrom);
		p.setInt(3, count);
		
		ResultSet rs = p.executeQuery();
		
		l = this.fillTopicsData(rs);
		
		rs.close();
		p.close();
		return l;
	}
	
	protected Topic getBaseTopicData(ResultSet rs) throws Exception
	{
		Topic t = new Topic();
		
		t.setTitle(rs.getString("topic_title"));
		t.setId(rs.getInt("topic_id"));
		t.setTime(rs.getTimestamp("topic_time"));
		t.setStatus(rs.getInt("topic_status"));
		t.setTotalViews(rs.getInt("topic_views"));
		t.setTotalReplies(rs.getInt("topic_replies"));
		t.setFirstPostId(rs.getInt("topic_first_post_id"));
		t.setLastPostId(rs.getInt("topic_last_post_id"));
		t.setType(rs.getInt("topic_type"));
		t.setForumId(rs.getInt("forum_id"));
		t.setModerated(rs.getInt("moderated") == 1);
		
		return t;
	}
	
	public List fillTopicsData(ResultSet rs) throws Exception
	{
		SimpleDateFormat df = new SimpleDateFormat(SystemGlobals.getValue(ConfigKeys.DATE_TIME_FORMAT));
		List l = new ArrayList();
		
		while (rs.next()) {
			Topic t = this.getBaseTopicData(rs);
			t.setHasAttach(rs.getInt("attach") > 0);

			// First Post Time
			t.setFirstPostTime(df.format(rs.getTimestamp("topic_time")));
			
			// Last Post Time
			t.setLastPostTime(df.format(rs.getTimestamp("post_time")));
			t.setLastPostTimeInMillis(rs.getTimestamp("post_time"));

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
	public List selectLastN(int count) throws Exception 
	{
		List topics = new ArrayList();
		
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.selectLastN"));
		p.setInt(1, count);
		
		ResultSet rs = p.executeQuery();
		
		// If you want more fields here, just put the code. At the time
		// this code was written, these were the only needed fields ;)
		while (rs.next()) {
			Topic t = new Topic();
			
			t.setTitle(rs.getString("topic_title"));
			t.setId(rs.getInt("topic_id"));
			t.setTime(rs.getTimestamp("topic_time"));
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
	public List notifyUsers(Topic topic) throws Exception 
	{ 
		int posterId = SessionFacade.getUserSession().getUserId();
		int anonUser = SystemGlobals.getIntValue(ConfigKeys.ANONYMOUS_USER_ID);
		
		PreparedStatement stmt = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.notifyUsers"));		
		ResultSet rs = null;

		stmt.setInt(1, topic.getId());
		stmt.setInt(2, posterId); //don't notify the poster
		stmt.setInt(3, anonUser); //don't notify the anonimous user
				
		rs = stmt.executeQuery();
		
		List users = new ArrayList();
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
	
	/** 
	 * @see net.jforum.model.TopicModel#selectRecentTopics(int)
	 */	
	public List selectRecentTopics (int limit) throws Exception
	{
		List l = new ArrayList();

		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.selectRecentTopicsByLimit"));
		p.setInt(1, limit);
		
		ResultSet rs = p.executeQuery();
		
		l = this.fillTopicsData(rs);
		
		rs.close();
		p.close();
		return l;		
	}
	
	/** 
	 * @see net.jforum.model.TopicModel#setFirstPostId(int, int)
	 */
	public void setFirstPostId(int topicId, int postId) throws Exception 
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.setFirstPostId"));
		p.setInt(1, postId);
		p.setInt(2, topicId);
		p.executeUpdate();
		p.close();
	}

	/** 
	 * @see net.jforum.model.TopicModel#getMinPostId(int)
	 */
	public int getMinPostId(int topicId) throws Exception 
	{
		int id = -1;
		
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.getMinPostId"));
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
	 * @see net.jforum.model.TopicModel#setModerationStatus(int, boolean)
	 */
	public void setModerationStatus(int forumId, boolean status) throws Exception
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.setModerationStatus"));
		p.setInt(1, status ? 1 : 0);
		p.setInt(2, forumId);
		p.executeUpdate();
		p.close();
	}
	
	/**
	 * @see net.jforum.model.TopicModel#selectTopicTitlesByIds(java.util.Collection)
	 */
	public List selectTopicTitlesByIds(Collection idList) throws Exception
	{
		List l = new ArrayList();
		String sql = SystemGlobals.getSql("TopicModel.selectTopicTitlesByIds");
		
		StringBuffer sb = new StringBuffer(idList.size() * 2);
		for (Iterator iter = idList.iterator(); iter.hasNext(); ) {
			sb.append(iter.next()).append(",");
		}
		
		int len = sb.length();
		sql = sql.replaceAll(":ids:", len > 0 ? sb.toString().substring(0, len - 1) : "0");
		PreparedStatement p = JForum.getConnection().prepareStatement(sql);
		
		ResultSet rs = p.executeQuery();
		while (rs.next()) {
			Map m = new HashMap();
			m.put("id", new Integer(rs.getInt("topic_id")));
			m.put("title", rs.getString("topic_title"));
			
			l.add(m);
		}
		
		rs.close();
		p.close();
		
		return l;
	}
}
