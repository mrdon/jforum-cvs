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
 * Created on 21.09.2004 
 * The JForum Project
 * http://www.jforum.net
 */
package net.jforum.drivers.hsqldb;


/**
 * @author Marc Wick
 * @version $Id: DataAccessDriver.java,v 1.4.8.1 2005/03/28 15:59:50 rafaelsteil Exp $
 */
public class DataAccessDriver extends
		net.jforum.drivers.postgresql.DataAccessDriver {

	private static PostModel postModel = new PostModel();
	private static UserModel userModel = new UserModel();
	private static TopicModel topicModel = new TopicModel();
	private static ModerationModel moderationModel = new ModerationModel();
	
	/**
	 * @see net.jforum.model.DataAccessDriver#newModerationModel()
	 */
	public net.jforum.model.ModerationModel newModerationModel() {
		return moderationModel;
	}

	/**
	 * @see net.jforum.model.DataAccessDriver#newPostModel()
	 */
	public net.jforum.model.PostModel newPostModel() {
		return postModel;
	}

	/**
	 * @see net.jforum.model.DataAccessDriver#newTopicModel()
	 */
	public net.jforum.model.TopicModel newTopicModel() {
		return topicModel;
	}
	
	/**
	 * @see net.jforum.model.DataAccessDriver#newUserModel()
	 */
	public net.jforum.model.UserModel newUserModel() {
		return userModel;
	}
}