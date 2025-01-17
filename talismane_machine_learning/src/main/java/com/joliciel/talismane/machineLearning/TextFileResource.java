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
package com.joliciel.talismane.machineLearning;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.joliciel.talismane.utils.JolicielException;
import com.joliciel.talismane.utils.WeightedOutcome;

/**
 * An external resource read from a text file.<br>
 * The first line must be "Type: KeyValue". <br>
 * The default name will be the filename.<br>
 * If a line starts with the string "Name: ", the default name will be replaced
 * by this name.<br>
 * All lines starting with # are skipped.<br>
 * Any other line will be broken up by tabs:<br>
 * One tab per key component, and the last tab is the class.<br>
 * 
 * @author Assaf Urieli
 *
 */
public class TextFileResource implements ExternalResource<String> {
  private static final long serialVersionUID = 1L;
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(TextFileResource.class);
  Map<String, List<WeightedOutcome<String>>> resultsMap = new HashMap<String, List<WeightedOutcome<String>>>();
  Map<String, String> resultMap = new HashMap<String, String>();

  private String name;

  public TextFileResource(String fileName, Scanner scanner) {
    this.name = fileName;

    int numParts = -1;
    int i = 1;
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      if (line.equals("Type: KeyValue"))
        continue;
      if (line.length() > 0 && !line.startsWith("#")) {
        StringBuilder sb = new StringBuilder();
        String[] parts = line.split("\t");
        if (parts.length == 1 && line.startsWith("Name: ")) {
          this.name = line.substring("Name: ".length());
          i++;
          continue;
        }

        if (numParts < 0)
          numParts = parts.length;
        if (parts.length != numParts)
          throw new JolicielException("Wrong number of elements on line " + i + " in file: " + fileName);

        for (int j = 0; j < numParts - 1; j++) {
          sb.append(parts[j]);
          sb.append("|");
        }
        String key = sb.toString();
        String value = parts[numParts - 1];
        resultMap.put(key, value);

      }
      i++;
    }
  }

  @Override
  public String getResult(List<String> keyElements) {
    StringBuilder sb = new StringBuilder();
    for (String keyElement : keyElements) {
      sb.append(keyElement);
      sb.append("|");
    }
    String key = sb.toString();
    String result = resultMap.get(key);

    return result;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

}
