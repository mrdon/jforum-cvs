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
 * This file creation date: 30/03/2003 / 02:37:20
 * The JForum Project
 * http://www.jforum.net
 */
package net.jforum.drivers.generic;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.jforum.JForum;
import net.jforum.entities.Forum;
import net.jforum.entities.LastPostInfo;
import net.jforum.entities.Topic;
import net.jforum.model.DataAccessDriver;
import net.jforum.model.TopicModel;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

/**
 * @author Rafael Steil
 * @author Vanessa Sabino
 * @version $Id: ForumModel.java,v 1.18.6.1 2005/03/28 15:59:46 rafaelsteil Exp $
 */
public class ForumModel extends AutoKeys implements net.jforum.model.ForumModel 
{
	/**
	 * @see net.jforum.model.ForumModel#selectById(int)
	 */
	public Forum selectById(int forumId) throws Exception {
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.selectById"));
		p.setInt(1, forumId);

		ResultSet rs = p.executeQuery();

		Forum f = new Forum();

		if (rs.next()) {
			f = this.fillForum(rs);
		}

		rs.close();
		p.close();

		return f;
	}

	protected Forum fillForum(ResultSet rs) throws Exception {
		Forum f = new Forum();

		f.setId(rs.getInt("forum_id"));
		f.setIdCategories(rs.getInt("categories_id"));
		f.setName(rs.getString("forum_name"));
		f.setDescription(rs.getString("forum_desc"));
		f.setOrder(rs.getInt("forum_order"));
		f.setTotalTopics(rs.getInt("forum_topics"));
		f.setLastPostId(rs.getInt("forum_last_post_id"));
		f.setModerated(rs.getInt("moderated") > 0);
		f.setTotalPosts(rs.getInt("total_posts"));

		return f;
	}

	/**
	 * @see net.jforum.model.ForumModel#selectAll()
	 */
	public List selectAll() throws Exception {
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.selectAll"));
		List l = new ArrayList();

		ResultSet rs = p.executeQuery();

		while (rs.next()) {
			l.add(this.fillForum(rs));
		}

		rs.close();
		p.close();

		return l;
	}

	/**
	 * @see net.jforum.model.ForumModel#setOrderUp(Forum, Forum)
	 */
	public Forum setOrderUp(Forum forum, Forum related) throws Exception 
	{
		return this.changeForumOrder(forum, related, true);
	}
	
	/**
	 * @see net.jforum.model.ForumModel#setOrderDown(Forum, Forum)
	 */
	public Forum setOrderDown(Forum forum, Forum related) throws Exception {
		return this.changeForumOrder(forum, related, false);
	}
	
	private Forum changeForumOrder(Forum forum, Forum related, boolean up) throws Exception
	{
		int tmpOrder = related.getOrder();
		related.setOrder(forum.getOrder());
		forum.setOrder(tmpOrder);

		// ******** 
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.setOrderById"));
		p.setInt(1, forum.getOrder());
		p.setInt(2, forum.getId());
		p.executeUpdate();
		p.close();
		
		// ********
		p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.setOrderById"));
		p.setInt(1, related.getOrder());
		p.setInt(2, related.getId());
		p.executeUpdate();
		p.close();
		
		return this.selectById(forum.getId());
	}

	/**
	 * @see net.jforum.model.ForumModel#delete(int)
	 */
	public void delete(int forumId) throws Exception {
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.delete"));
		p.setInt(1, forumId);

		p.executeUpdate();

		p.close();
	}

	/**
	 * @see net.jforum.model.ForumModel#update(net.jforum.Forum)
	 */
	public void update(Forum forum) throws Exception {
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.update"));

		p.setInt(1, forum.getCategoryId());
		p.setString(2, forum.getName());
		p.setString(3, forum.getDescription());
		p.setInt(4, forum.isModerated() ? 1 : 0);
		p.setInt(5, forum.getId());

		// Order, TotalTopics and LastPostId must be updated using the
		// respective methods
		p.executeUpdate();
		p.close();
	}

	/**
	 * @see net.jforum.model.ForumModel#addNew(net.jforum.Forum)
	 */
	public int addNew(Forum forum) throws Exception {
		// Gets the higher order
		PreparedStatement pOrder = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.getMaxOrder"));
		ResultSet rs = pOrder.executeQuery();

		if (rs.next()) {
			forum.setOrder(rs.getInt(1) + 1);
		}

		rs.close();
		pOrder.close();

		// Updates the order
		PreparedStatement p = this.getStatementForAutoKeys("ForumModel.addNew");

		p.setInt(1, forum.getCategoryId());
		p.setString(2, forum.getName());
		p.setString(3, forum.getDescription());
		p.setInt(4, forum.getOrder());
		p.setInt(5, forum.isModerated() ? 1 : 0);

		int forumId = this.executeAutoKeysQuery(p);

		p.close();

		forum.setId(forumId);
		return forumId;
	}

	/**
	 * @see net.jforum.model.ForumModel#setLastPost(int, int)
	 */
	public void setLastPost(int forumId, int postId) throws Exception {
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.updateLastPost"));

		p.setInt(1, postId);
		p.setInt(2, forumId);

		p.executeUpdate();

		p.close();
	}

	/**
	 * @see net.jforum.model.ForumModel#setTotalTopics(int)
	 */
	public void incrementTotalTopics(int forumId, int count) throws Exception {
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.incrementTotalTopics"));
		p.setInt(1, count);
		p.setInt(2, forumId);
		p.executeUpdate();
		p.close();
	}

	/**
	 * @see net.jforum.model.ForumModel#setTotalTopics(int)
	 */
	public void decrementTotalTopics(int forumId, int count) throws Exception {
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.decrementTotalTopics"));
		p.setInt(1, count);
		p.setInt(2, forumId);
		p.executeUpdate();
		p.close();

		// If there are no more topics, then clean the
		// last post id information
		int totalTopics = this.getTotalTopics(forumId);
		if (totalTopics < 1) {
			this.setLastPost(forumId, 0);
		}
	}
	
	private LastPostInfo getLastPostInfo(int forumId, boolean tryFix) throws Exception
	{
		LastPostInfo lpi = new LastPostInfo();

		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.lastPostInfo"));
		p.setInt(1, forumId);

		ResultSet rs = p.executeQuery();

		if (rs.next()) {
			lpi.setUsername(rs.getString("username"));
			lpi.setUserId(rs.getInt("user_id"));

			SimpleDateFormat df = new SimpleDateFormat(SystemGlobals.getValue(ConfigKeys.DATE_TIME_FORMAT));
			lpi.setPostDate(df.format(rs.getTimestamp("post_time")));
			lpi.setPostId(rs.getInt("post_id"));
			lpi.setTopicId(rs.getInt("topic_id"));
			lpi.setPostTimeMillis(rs.getTimestamp("post_time").getTime());
			lpi.setTopicReplies(rs.getInt("topic_replies"));

			lpi.setHasInfo(true);
			
			// Check if the topic is consistent
			TopicModel tm = DataAccessDriver.getInstance().newTopicModel();
			Topic t = tm.selectById(lpi.getTopicId());
			
			if (t.getId() == 0) {
				// Hm, that's not good. Try to fix it
				tm.fixFirstLastPostId(lpi.getTopicId());
			}
			
			tryFix = false;
		}
		else if (tryFix) {
			p.close();
			rs.close();
			
			int postId = this.getMaxPostId(forumId);
			
			p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.latestTopicIdForfix"));
			p.setInt(1, postId);
			rs = p.executeQuery();
			
			int topicId = -1;
			
			if (rs.next()) {
				topicId = rs.getInt("topic_id");
				
				rs.close();
				p.close();
				
				p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.fixLatestPostData"));
				p.setInt(1, postId);
				p.setInt(2, topicId);
				p.executeUpdate();
			}
		}

		p.close();
		rs.close();
		
		return (tryFix ? this.getLastPostInfo(forumId, false) : lpi);
	}
	/**
	 * @see net.jforum.model.ForumModel#getLastPostInfo(int)
	 */
	public LastPostInfo getLastPostInfo(int forumId) throws Exception 
	{
		return this.getLastPostInfo(forumId, true);
	}

	/**
	 * @see net.jforum.model.ForumModel#getTotalMessages()
	 */
	public int getTotalMessages() throws Exception {
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.totalMessages"));
		ResultSet rs = p.executeQuery();
		rs.next();

		int total = rs.getInt("total_messages");
		p.close();
		rs.close();

		return total;
	}

	/**
	 * @see net.jforum.model.ForumModel#getTotalTopics(int)
	 */
	public int getTotalTopics(int forumId) throws Exception {
		int total = 0;
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.getTotalTopics"));
		p.setInt(1, forumId);
		ResultSet rs = p.executeQuery();

		if (rs.next()) {
			total = rs.getInt(1);
		}

		p.close();
		rs.close();

		return total;
	}

	/**
	 * @see net.jforum.model.ForumModel#getMaxPostId(int)
	 */
	public int getMaxPostId(int forumId) throws Exception {
		int id = -1;

		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.getMaxPostId"));
		p.setInt(1, forumId);

		ResultSet rs = p.executeQuery();
		if (rs.next()) {
			id = rs.getInt("post_id");
		}

		rs.close();
		p.close();

		return id;
	}

	/**
	 * @see net.jforum.model.ForumModel#moveTopics(java.lang.String[], int, int)
	 */
	public void moveTopics(String[] topics, int fromForumId, int toForumId) throws Exception {
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.moveTopics"));
		PreparedStatement t = JForum.getConnection().prepareStatement(SystemGlobals.getSql("PostModel.setForumByTopic"));

		p.setInt(1, toForumId);
		t.setInt(1, toForumId);

		for (int i = 0; i < topics.length; i++) {
			int topicId = Integer.parseInt(topics[i]);
			p.setInt(2, topicId);
			t.setInt(2, topicId);

			p.executeUpdate();
			t.executeUpdate();
		}

		this.decrementTotalTopics(fromForumId, topics.length);
		this.incrementTotalTopics(toForumId, topics.length);

		p.close();
	}
	
	/**
	 * @see net.jforum.model.ForumModel#hasUnreadTopics(int, long)
	 */
	public List checkUnreadTopics(int forumId, long lastVisit) throws Exception
	{
		List l = new ArrayList();
		
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.checkUnreadTopics"));
		p.setInt(1, forumId);
		p.setTimestamp(2, new Timestamp(lastVisit));
		
		ResultSet rs = p.executeQuery();
		while (rs.next()) {
			Topic t = new Topic();
			t.setId(rs.getInt("topic_id"));
			t.setTime(new Date(rs.getTimestamp(1).getTime()));
			
			l.add(t);
		}
		
		rs.close();
		p.close();
		
		return l;
	}
	
	/**
	 * @see net.jforum.model.ForumModel#setModerated(int, boolean)
	 */
	public void setModerated(int categoryId, boolean status) throws Exception
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.setModerated"));
		p.setInt(1, status ? 1 : 0);
		p.setInt(2, categoryId);
		p.executeUpdate();
		p.close();
	}
}
