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
 * This file creation date: Mar 28, 2003 / 22:57:43 PM
 * The JForum Project
 * http://www.jforum.net
 */
package net.jforum.drivers.generic;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.jforum.JForum;
import net.jforum.entities.Post;
import net.jforum.model.DataAccessDriver;
import net.jforum.model.SearchIndexerModel;
import net.jforum.util.concurrent.executor.QueuedExecutor;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;
import net.jforum.util.search.MessageIndexerTask;

/**
 * @author Rafael Steil
 * @author Vanessa Sabino
 * @version $Id: PostModel.java,v 1.20.4.1 2005/03/28 15:59:46 rafaelsteil Exp $
 */
public class PostModel extends AutoKeys implements net.jforum.model.PostModel 
{
	/**
	 * @see net.jforum.model.PostModel#selectById(int)
	 */
	public Post selectById(int postId) throws Exception 
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("PostModel.selectById"));		
		p.setInt(1, postId);
		
		ResultSet rs = p.executeQuery();
		
		Post post = new Post();
		
		if (rs.next()) {
			post = this.makePost(rs);
		}
		
		rs.close();
		p.close();
		
		return post;
	}
	
	protected Post makePost(ResultSet rs) throws Exception
	{
		Post post = new Post();
		post.setId(rs.getInt("post_id"));
		post.setTopicId(rs.getInt("topic_id"));
		post.setForumId(rs.getInt("forum_id"));
		post.setUserId(rs.getInt("user_id"));
		Timestamp postTime = rs.getTimestamp("post_time");
		post.setTime(postTime);
		post.setUserIp(rs.getString("poster_ip"));
		post.setBbCodeEnabled(rs.getInt("enable_bbcode") > 0);
		post.setHtmlEnabled(rs.getInt("enable_html") > 0);
		post.setSmiliesEnabled(rs.getInt("enable_smilies") > 0);
		post.setSignatureEnabled(rs.getInt("enable_sig") > 0);
		post.setEditTime(rs.getTimestamp("post_edit_time"));
		post.setEditCount(rs.getInt("post_edit_count"));
		post.setSubject(rs.getString("post_subject"));
		post.setText(this.getPostTextFromResultSet(rs));
		post.setPostUsername(rs.getString("username"));
		post.hasAttachments(rs.getInt("attach") > 0);
		
		SimpleDateFormat df = new SimpleDateFormat(SystemGlobals.getValue(ConfigKeys.DATE_TIME_FORMAT));
		post.setFormatedTime(df.format(postTime));
		
		post.setKarma(DataAccessDriver.getInstance().newKarmaModel().getPostKarma(post.getId()));
		
		return post;
	}
	
	/**
	 * Utility method to read the post text fromt the result set.
	 * This method may be useful when using some "non-standart" way
	 * to store text, like oracle does when using (c|b)lob
	 * 
	 * @param rs The resultset to fetch data from
	 * @return The post text string
	 * @throws Exception
	 */
	protected String getPostTextFromResultSet(ResultSet rs) throws Exception
	{
		return rs.getString("post_text");
	}

	/**
	 * @see net.jforum.model.PostModel#delete(Post)
	 */
	public void delete(final Post post) throws Exception 
	{
		this.removePosts(new ArrayList() {{ add(post); }});
	}
	
	private void removePosts(List posts) throws Exception
	{
		PreparedStatement post = JForum.getConnection().prepareStatement(SystemGlobals.getSql("PostModel.deletePost"));
		PreparedStatement text = JForum.getConnection().prepareStatement(SystemGlobals.getSql("PostModel.deletePostText"));
		PreparedStatement search = JForum.getConnection().prepareStatement(SystemGlobals.getSql("PostModel.deleteWordMatch"));
		
		for (Iterator iter = posts.iterator(); iter.hasNext(); ) {
			Post p = (Post)iter.next();

			post.setInt(1, p.getId());
			text.setInt(1, p.getId());
			search.setInt(1, p.getId());
			
			text.executeUpdate();
			search.executeUpdate();
			post.executeUpdate();
		}
		
		search.close();
		post.close();
		text.close();
	}
	
	/**
	 * @set net.jforum.model.PostModel#deleteByTopic(int) 
	 */
	public void deleteByTopic(int topicId) throws Exception
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("PostModel.deleteByTopic"));
		p.setInt(1, topicId);
		ResultSet rs = p.executeQuery();
		
		List posts = new ArrayList();
		while (rs.next()) {
			Post post = new Post();
			post.setId(rs.getInt("post_id"));
			post.setUserId(rs.getInt("user_id"));
			
			posts.add(post);
		}
		
		rs.close();
		p.close();
		
		this.removePosts(posts);
	}
	
	/**
	 * @see net.jforum.model.PostModel#update(net.jforum.Post)
	 */
	public void update(Post post) throws Exception 
	{
		this.updatePostsTable(post);
		this.updatePostsTextTable(post);
	}
	
	protected void updatePostsTextTable(Post post) throws Exception
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("PostModel.updatePostText"));
		p.setString(1, post.getText());
		p.setString(2, post.getSubject());
		p.setInt(3, post.getId());
		
		p.executeUpdate();
		p.close();
	}
	
	protected void updatePostsTable(Post post) throws Exception
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("PostModel.updatePost"));
		p.setInt(1, post.getTopicId());
		p.setInt(2, post.getForumId());
		p.setInt(3, post.isBbCodeEnabled() ? 1 : 0);
		p.setInt(4, post.isHtmlEnabled() ? 1 : 0);
		p.setInt(5, post.isSmiliesEnabled() ? 1 : 0);
		p.setInt(6, post.isSignatureEnabled() ? 1 : 0);
		p.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
		p.setString(8, post.getUserIp());
		p.setInt(9, post.getId());
		
		p.executeUpdate();
		p.close();
	}

	/**
	 * @see net.jforum.model.PostModel#addNew(net.jforum.Post)
	 */
	public int addNew(Post post) throws Exception 
	{
		this.addNewPost(post);
		this.addNewPostText(post);
		
		// Tokenize the words for search
		if (SystemGlobals.getBoolValue(ConfigKeys.BACKGROUND_TASKS)) {
			QueuedExecutor.getInstance().execute(new MessageIndexerTask(post));
		}
		else {
			SearchIndexerModel indexer = DataAccessDriver.getInstance().newSearchIndexerModel();
			indexer.setConnection(JForum.getConnection());
			indexer.insertSearchWords(post);
		}
		
		return post.getId();
	}
	
	protected void addNewPostText(Post post) throws Exception
	{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("PostModel.addNewPostText"));
		p.setInt(1, post.getId());
		p.setString(2, post.getText());
		p.setString(3, post.getSubject());
		p.executeUpdate();
		p.close();
	}
	
	protected void addNewPost(Post post) throws Exception
	{
		PreparedStatement p = this.getStatementForAutoKeys("PostModel.addNewPost");
		
		p.setInt(1, post.getTopicId());
		p.setInt(2, post.getForumId());
		p.setLong(3, post.getUserId());
		p.setTimestamp(4, new Timestamp(post.getTime().getTime()));
		p.setString(5, post.getUserIp());
		p.setInt(6, post.isBbCodeEnabled() ? 1 : 0);
		p.setInt(7, post.isHtmlEnabled() ? 1 : 0);
		p.setInt(8, post.isSmiliesEnabled() ? 1 : 0);
		p.setInt(9, post.isSignatureEnabled() ? 1 : 0);
		p.setInt(10, post.isModerationNeeded() ? 1 : 0);
		
		int postId = this.executeAutoKeysQuery(p);
		post.setId(postId);
		
		p.close();
	}
	
	/**
	 * @see net.jforum.model.PostModel#setLastPostIdInTopic(int, int)
	 */
	public void setLastPostIdInTopic(int postId, int topicId) throws Exception{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("TopicModel.setLastPostId"));
		p.setInt(1, postId);
		p.setInt(2, topicId);
		p.executeUpdate();
		p.close();
	}
	
	/**
	 * @see net.jforum.model.PostModel#setLastPostIdInForum(int, int)
	 */
	public void setLastPostIdInForum(int postId, int forumId) throws Exception{
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("ForumModel.updateLastPost"));
		p.setInt(1, postId);
		p.setInt(2, forumId);
		p.executeUpdate();
		p.close();
	}

	/**
	 * @see net.jforum.model.PostModel#selectAllBytTopic(int)
	 */
	public List selectAllByTopic(int topicId) throws Exception
	{
		return this.selectAllByTopicByLimit(topicId, 0, Integer.MAX_VALUE);
	}
	
	/**
	 * @see net.jforum.model.PostModel#selectAllBytTopicByLimit(int, int, int)
	 */
	public List selectAllByTopicByLimit(int topicId, int startFrom, int count) throws Exception
	{
		List l = new ArrayList();
		
		PreparedStatement p = JForum.getConnection().prepareStatement(SystemGlobals.getSql("PostModel.selectAllByTopicByLimit"));
		p.setInt(1, topicId);
		p.setInt(2, startFrom);
		p.setInt(3, count);
		
		ResultSet rs = p.executeQuery();
						
		while (rs.next()) {
			l.add(this.makePost(rs));			
		}
		
		rs.close();
		p.close();
				
		return l;
	}
}
