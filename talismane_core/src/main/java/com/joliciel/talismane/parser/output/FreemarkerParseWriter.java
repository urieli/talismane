///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2014 Joliciel Informatique
//
//This file is part of Talismane.
//
//Talismane is free software: you can redistribute it and/or modify
//it under the terms of the GNU Affero General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//Talismane is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU Affero General Public License for more details.
//
//You should have received a copy of the GNU Affero General Public License
//along with Talismane.  If not, see <http://www.gnu.org/licenses/>.
//////////////////////////////////////////////////////////////////////////////
package com.joliciel.talismane.parser.output;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.joliciel.talismane.Talismane;
import com.joliciel.talismane.Talismane.BuiltInTemplate;
import com.joliciel.talismane.TalismaneException;
import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.parser.ParseConfiguration;
import com.joliciel.talismane.utils.ConfigUtils;
import com.joliciel.talismane.utils.LogUtils;
import com.typesafe.config.Config;

import freemarker.cache.NullCacheStorage;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;

/**
 * Processes parser output by writing via a freemarker template. Will either use
 * the built-in template specified in
 * talismane.core.[sessionId].parser.output.built-in-template, or any other
 * template specified in talismane.core.[sessionId].parser.output.template.<br>
 * If no writer is specified, will write to a file with the suffix "_dep.txt".
 * 
 * @author Assaf Urieli
 *
 */
public class FreemarkerParseWriter implements ParseConfigurationProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(FreemarkerParseWriter.class);
  private final Template template;
  private final Writer writer;
  private int sentenceCount = 0;
  private int tokenCount = 0;
  private int relationCount = 0;
  private int characterCount = 0;

  public FreemarkerParseWriter(File outDir, String sessionId) throws IOException {
    this(new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(new File(outDir, TalismaneSession.get(sessionId).getBaseName() + "_dep.txt"), false),
          TalismaneSession.get(sessionId).getOutputCharset())), sessionId);
  }

  public FreemarkerParseWriter(Writer writer, String sessionId) throws IOException {
    Config config = ConfigFactory.load();
    Config parserConfig = config.getConfig("talismane.core." + sessionId + ".parser");

    this.writer = writer;

    Reader templateReader = null;
    String configPath = "talismane.core." + sessionId + ".parser.output.template";
    if (config.hasPath(configPath)) {
      templateReader = new BufferedReader(new InputStreamReader(ConfigUtils.getFileFromConfig(config, configPath)));
    } else {
      String templateName = null;
      BuiltInTemplate builtInTemplate = BuiltInTemplate.valueOf(parserConfig.getString("output.built-in-template"));
      switch (builtInTemplate) {
      case standard:
        templateName = "parser_conll_template.ftl";
        break;
      case with_location:
        templateName = "parser_conll_template_with_location.ftl";
        break;
      case with_prob:
        templateName = "parser_conll_template_with_prob.ftl";
        break;
      case with_comments:
        templateName = "parser_conll_template_with_comments.ftl";
        break;
      case original:
        templateName = "parser_conll_template_original.ftl";
        break;
      default:
        throw new RuntimeException("Unknown builtInTemplate for parser: " + builtInTemplate.name());
      }

      String path = "output/" + templateName;
      InputStream inputStream = Talismane.class.getResourceAsStream(path);
      if (inputStream == null)
        throw new IOException("Resource not found in classpath: " + path);
      templateReader = new BufferedReader(new InputStreamReader(inputStream));
    }

    this.template = this.getTemplate(templateReader);
  }

  public FreemarkerParseWriter(Reader templateReader, Writer writer, String sessionId) throws IOException, TalismaneException {
    this.writer = writer;
    this.template = this.getTemplate(templateReader);
  }

  private Template getTemplate(Reader templateReader) throws IOException {
    Configuration cfg = new Configuration(new Version(2, 3, 23));
    cfg.setCacheStorage(new NullCacheStorage());
    cfg.setObjectWrapper(new DefaultObjectWrapper(new Version(2, 3, 23)));
    return new Template("freemarkerTemplate", templateReader, cfg);
  }

  void process(Map<String, Object> model) throws IOException {
    try {
      template.process(model, writer);
      writer.flush();
    } catch (TemplateException te) {
      LogUtils.logError(LOG, te);
      throw new RuntimeException(te);
    }
  }

  @Override
  public void onNextParseConfiguration(ParseConfiguration parseConfiguration) throws IOException {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Outputting: " + parseConfiguration.toString());
    }
    Map<String, Object> model = new HashMap<String, Object>();
    ParseConfigurationOutput output = new ParseConfigurationOutput(parseConfiguration);
    model.put("sentence", output);
    model.put("sentenceNumber", sentenceCount + 1);
    model.put("configuration", parseConfiguration);
    model.put("tokenCount", tokenCount);
    model.put("relationCount", relationCount);
    model.put("sentenceCount", sentenceCount);
    model.put("characterCount", characterCount);
    model.put("LOG", LOG);
    this.process(model);
    tokenCount += parseConfiguration.getPosTagSequence().size();
    relationCount += parseConfiguration.getRealDependencies().size();
    characterCount += parseConfiguration.getSentence().getText().length();
    sentenceCount += 1;
  }

  @Override
  public void onCompleteParse() {
    // nothing to do here
  }

  @Override
  public void close() throws IOException {
    this.writer.close();
  }
}
