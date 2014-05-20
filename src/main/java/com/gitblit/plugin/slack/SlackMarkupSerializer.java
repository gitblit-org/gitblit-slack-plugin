/*
 * Copyright 2014 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.plugin.slack;

import org.pegdown.LinkRenderer;
import org.pegdown.ToHtmlSerializer;
import org.pegdown.ast.SimpleNode;
import org.pegdown.ast.SuperNode;
import org.pegdown.ast.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Slack only supports a minimal set of markup syntax.  This class helps generate Slack-friendly
 * content from richer Markdown text.  If there is no equivalent Slack markup, the plain HTML is
 * injected.  This is not ideal, but it's better than injecting all HTML messages into your Slack
 * channels.
 *
 * @author James Moger
 *
 */
public class SlackMarkupSerializer extends ToHtmlSerializer {

	private final Logger log = LoggerFactory.getLogger(getClass());

	public SlackMarkupSerializer() {
		super(new LinkRenderer());
	}

	@Override
	 public void visit(SimpleNode node) {
	        switch (node.getType()) {
	            case Linebreak:
	                printer.print("\n");
	                break;
	            case Nbsp:
	                printer.print(" ");
	                break;
	            default:
	            	super.visit(node);
	        }
	    }

    @Override
	protected void printTag(TextNode node, String tag) {
       	String pre = "";
    	String post = "";
    	switch (tag.toLowerCase()) {
    	case "b":
    	case "strong":
    		pre = post = "*";
    		break;
    	case "i":
    	case "em":
    		pre = post = "_";
    		break;
    	case "p":
    		post = "\n\n";
    		break;
    	case "br":
    		post = "\n";
    		break;
    	case "code":
    		pre = post = "`";
    		break;
    	case "pre":
    		pre = post = "```\n";
    		break;
    	case "blockquote":
    		pre = "> ";
    		break;
    	default:
    		// unsupported transform type
    		log.warn("Slack does not offer a markup substitute for tag {}", tag);
    		pre = String.format("<%s>", tag);
    		post = String.format("</%s>", tag);
    	}

    	printer.print(pre);
        printer.printEncoded(node.getText());
        printer.print(post);
    }

    @Override
	protected void printTag(SuperNode node, String tag) {
    	String pre = "";
    	String post = "";
    	switch (tag.toLowerCase()) {
    	case "b":
    	case "strong":
    		pre = post = "*";
    		break;
    	case "i":
    	case "em":
    		pre = post = "_";
    		break;
    	case "p":
    		post = "\n\n";
    		break;
    	case "br":
    		post = "\n";
    		break;
    	case "code":
    		pre = post = "`";
    		break;
    	case "pre":
    		pre = post = "```\n";
    		break;
    	case "blockquote":
    		pre = "> ";
    		break;
    	default:
    		// unsupported transform type
    		log.warn("Slack does not offer a markup substitute for tag {}", tag);
    		pre = String.format("<%s>", tag);
    		post = String.format("</%s>", tag);
    	}

        printer.print(pre);
        visitChildren(node);
        printer.print(post);
    }

    @Override
	protected void printIndentedTag(SuperNode node, String tag) {
        printTag(node, tag);
    }

    @Override
	protected void printImageTag(LinkRenderer.Rendering rendering) {
        printer.print('<').print(rendering.href).print('>');
    }

    @Override
	protected void printLink(LinkRenderer.Rendering rendering) {
        printer.print('<').print(rendering.href).print('|').print(rendering.text).print('>');
    }
}
