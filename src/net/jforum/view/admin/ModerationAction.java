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
 * Created on Jan 30, 2005 2:49:29 PM
 * The JForum Project
 * http://www.jforum.net
 */
package net.jforum.view.admin;

import java.sql.Connection;

import javax.servlet.http.HttpServletResponse;

import net.jforum.ActionServletRequest;
import net.jforum.Command;
import net.jforum.entities.Post;
import net.jforum.entities.Topic;
import net.jforum.model.DataAccessDriver;
import net.jforum.model.PostModel;
import net.jforum.model.TopicModel;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.PostRepository;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;
import net.jforum.view.forum.common.AttachmentCommon;
import net.jforum.view.forum.common.PostCommon;
import net.jforum.view.forum.common.TopicsCommon;
import net.jforum.view.forum.common.ViewCommon;
import freemarker.template.SimpleHash;
import freemarker.template.Template;

/**
 * @author Rafael Steil
 * @version $Id: ModerationAction.java,v 1.4.6.1 2005/03/28 15:59:53 rafaelsteil Exp $
 */
public class ModerationAction extends Command
{
	/**
	 * @see net.jforum.Command#list()
	 */
	public void list() throws Exception
	{
		this.context.put("moduleAction", "moderation_list.htm");
		this.context.put("infoList", DataAccessDriver.getInstance().newModerationModel().categoryPendingModeration());
	}
	
	public void view() throws Exception
	{
		int forumId = this.request.getIntParameter("forum_id");
		
		int start = ViewCommon.getStartPage();
		int count = SystemGlobals.getIntValue(ConfigKeys.POST_PER_PAGE);
		
		this.context.put("moduleAction", "moderation_list_posts.htm");
		this.context.put("forum", ForumRepository.getForum(forumId));
		this.context.put("topics", DataAccessDriver.getInstance().newModerationModel().topicsByForum(
				forumId, start, count));
	}
	
	public void save() throws Exception
	{
		String[] posts = this.request.getParameterValues("post_id");
		if (posts != null) {
			TopicModel tm = DataAccessDriver.getInstance().newTopicModel();
			
			for (int i = 0; i < posts.length; i++) {
				int postId = Integer.parseInt(posts[i]);
				
				String status = this.request.getParameter("status_" + postId);
				if ("defer".startsWith(status)) {
					continue;
				}
				
				if ("aprove".startsWith(status)) {
					Post p = DataAccessDriver.getInstance().newPostModel().selectById(postId);
					Topic t = tm.selectRaw(p.getTopicId());
					
					DataAccessDriver.getInstance().newModerationModel().aprovePost(postId);
					TopicsCommon.updateBoardStatus(t, postId, t.getFirstPostId() == postId,
							tm, DataAccessDriver.getInstance().newForumModel());
					
					
					DataAccessDriver.getInstance().newUserModel().incrementPosts(p.getUserId());
					
					if (SystemGlobals.getBoolValue(ConfigKeys.POSTS_CACHE_ENABLED)) {
						PostRepository.append(p.getTopicId(), PostCommon.preparePostForDisplay(p));
					}
				}
				else {
					PostModel pm = DataAccessDriver.getInstance().newPostModel();
					Post post = pm.selectById(postId);
					pm.delete(post);
					
					new AttachmentCommon(this.request).deleteAttachments(postId, post.getForumId());
					
					int totalPosts = tm.getTotalPosts(post.getTopicId());
					if (totalPosts == 0) {
						TopicsCommon.deleteTopic(post.getTopicId(), post.getForumId(), true);
					}
				}
			}
		}
		
		this.view();
	}

	/**
	 * @see net.jforum.Command#process(net.jforum.ActionServletRequest, javax.servlet.http.HttpServletResponse, java.sql.Connection, freemarker.template.SimpleHash)
	 */
	public Template process(ActionServletRequest request, HttpServletResponse response, Connection conn,
			SimpleHash context) throws Exception
	{
		if (AdminAction.isAdmin()) {
			super.process(request, response, conn, context);
		}
		
		return AdminAction.adminBaseTemplate();
	}
}
