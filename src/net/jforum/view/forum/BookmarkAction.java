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
 * Created on Jan 16, 2005 4:48:39 PM
 * The JForum Project
 * http://www.jforum.net
 */
package net.jforum.view.forum;

import java.sql.Connection;

import javax.servlet.http.HttpServletResponse;

import freemarker.template.SimpleHash;
import freemarker.template.Template;
import net.jforum.ActionServletRequest;
import net.jforum.Command;
import net.jforum.SessionFacade;
import net.jforum.entities.Bookmark;
import net.jforum.entities.BookmarkType;
import net.jforum.entities.Forum;
import net.jforum.entities.Topic;
import net.jforum.entities.User;
import net.jforum.model.BookmarkModel;
import net.jforum.model.DataAccessDriver;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.SecurityRepository;
import net.jforum.security.SecurityConstants;
import net.jforum.util.I18n;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

/**
 * @author Rafael Steil
 * @version $Id: BookmarkAction.java,v 1.2 2005/01/26 12:57:14 rafaelsteil Exp $
 */
public class BookmarkAction extends Command
{
	public void insert() throws Exception
	{
		int type = this.request.getIntParameter("relation_type");
		if (type == BookmarkType.FORUM) {
			this.addForum();
		}
		else if (type == BookmarkType.TOPIC) {
			this.addTopic();
		}
		else if (type == BookmarkType.USER) {
			this.addUser();
		}
		else {
			this.error("Bookmarks.invalidType");
		}
	}
	
	private void addForum() throws Exception
	{
		Forum f = ForumRepository.getForum(this.request.getIntParameter("relation_id"));
		String title = f.getName();
		String description = f.getDescription();
		
		Bookmark b = DataAccessDriver.getInstance().newBookmarkModel().selectForUpdate(
				f.getId(), BookmarkType.FORUM, SessionFacade.getUserSession().getUserId());
		if (b != null) {
			if (b.getTitle() != null) {
				title = b.getTitle();
			}
			
			if (b.getDescription() != null) {
				description = b.getDescription();
			}

			this.context.put("bookmark", b);
		}
		
		this.context.put("moduleAction", "bookmark_insert.htm");
		this.context.put("title", title);
		this.context.put("description", description);
		this.context.put("relationType", new Integer(BookmarkType.FORUM));
		this.context.put("relationId", new Integer(f.getId()));
	}
	
	private void addTopic() throws Exception
	{
		Topic t = DataAccessDriver.getInstance().newTopicModel().selectById(
				this.request.getIntParameter("relation_id"));
		String title = t.getTitle();
		
		Bookmark b = DataAccessDriver.getInstance().newBookmarkModel().selectForUpdate(
				t.getId(), BookmarkType.TOPIC, SessionFacade.getUserSession().getUserId());
		if (b != null) {
			if (b.getTitle() != null) {
				title = b.getTitle();
			}
			
			this.context.put("description", b.getDescription());
			this.context.put("bookmark", b);
		}
		
		this.context.put("moduleAction", "bookmark_insert.htm");
		this.context.put("title", title);
		this.context.put("relationType", new Integer(BookmarkType.TOPIC));
		this.context.put("relationId", new Integer(t.getId()));
	}
	
	private void addUser() throws Exception
	{
		User u = DataAccessDriver.getInstance().newUserModel().selectById(
				this.request.getIntParameter("relation_id"));
		String title = u.getUsername();
		
		Bookmark b = DataAccessDriver.getInstance().newBookmarkModel().selectForUpdate(
				u.getId(), BookmarkType.USER, SessionFacade.getUserSession().getUserId());
		if (b != null) {
			if (b.getTitle() != null) {
				title = b.getTitle();
			}
			
			this.context.put("description", b.getDescription());
			this.context.put("bookmark", b);
		}
		
		this.context.put("moduleAction", "bookmarks_insert.htm");
		this.context.put("title", title);
		this.context.put("relationType", new Integer(BookmarkType.USER));
		this.context.put("relationId", new Integer(u.getId()));
	}
	
	public void insertSave() throws Exception
	{
		Bookmark b = new Bookmark();
		b.setDescription(this.request.getParameter("description"));
		b.setTitle(this.request.getParameter("title"));
		b.setPublicVisible(this.request.getParameter("visible") != null);
		b.setRelationId(this.request.getIntParameter("relation_id"));
		b.setRelationType(this.request.getIntParameter("relation_type"));
		b.setUserId(SessionFacade.getUserSession().getUserId());
		
		DataAccessDriver.getInstance().newBookmarkModel().add(b);
		this.context.put("moduleAction", "bookmark_done.htm");
	}
	
	public void updateSave() throws Exception
	{
		int id = this.request.getIntParameter("bookmark_id");
		BookmarkModel bm = DataAccessDriver.getInstance().newBookmarkModel();
		Bookmark b = bm.selectById(id);
		
		if (!this.sanityCheck(b)) {
			return;
		}
		
		b.setTitle(this.request.getParameter("title"));
		b.setDescription(this.request.getParameter("description"));
		
		String visible = this.request.getParameter("visible");
		b.setPublicVisible(visible != null && !"".equals(visible) ? true : false);
		
		bm.update(b);
		this.context.put("moduleAction", "bookmark_done.htm");
	}
	
	public void edit() throws Exception
	{
		int id = this.request.getIntParameter("bookmark_id");
		BookmarkModel bm = DataAccessDriver.getInstance().newBookmarkModel();
		Bookmark b = bm.selectById(id);
		
		if (!this.sanityCheck(b)) {
			return;
		}
		
		this.context.put("moduleAction", "bookmark_insert.htm");
		this.context.put("bookmark", b);
	}
	
	private boolean sanityCheck(Bookmark b)
	{
		if (b == null) {
			this.error("Bookmarks.notFound");
			return false;
		}
		
		if (b.getUserId() != SessionFacade.getUserSession().getUserId()) {
			this.error("Bookmarks.notOwner");
			return false;
		}
		
		return true;
	}
	
	private void error(String message)
	{
		this.context.put("moduleAction", "message.htm");
		this.context.put("message", I18n.getMessage(message));
	}
	
	public void disabled()
	{
		this.error("Bookmarks.featureDisabled");
	}
	
	public void anonymousIsDenied()
	{
		this.error("Bookmarks.anonymousIsDenied");
	}

	/**
	 * @see net.jforum.Command#list()
	 */
	public void list() throws Exception
	{
		int userId = this.request.getIntParameter("user_id");
		
		this.context.put("moduleAction", "bookmark_list.htm");
		this.context.put("bookmarks", DataAccessDriver.getInstance().newBookmarkModel().selectByUser(userId));
		this.context.put("forumType", new Integer(BookmarkType.FORUM));
		this.context.put("userType", new Integer(BookmarkType.USER));
		this.context.put("topicType", new Integer(BookmarkType.TOPIC));
		
		this.setTemplateName(null);
	}
	
	/**
	 * @see net.jforum.Command#process(net.jforum.ActionServletRequest, javax.servlet.http.HttpServletResponse, java.sql.Connection, freemarker.template.SimpleHash)
	 */
	public Template process(ActionServletRequest request, HttpServletResponse response, Connection conn,
			SimpleHash context) throws Exception
	{
		if (SessionFacade.getUserSession().getUserId() == SystemGlobals.getIntValue(ConfigKeys.ANONYMOUS_USER_ID)) {
			request.addParameter("action", "anonymousIsDenied");
		}
		else if (!SecurityRepository.canAccess(SecurityConstants.PERM_BOOKMARKS_ENABLED)) {
			request.addParameter("action", "disabled");
		}
		else {
			this.setTemplateName(SystemGlobals.getValue(ConfigKeys.TEMPLATE_NAME) + "/empty.htm");
		}

		return super.process(request, response, conn, context);
	}
}