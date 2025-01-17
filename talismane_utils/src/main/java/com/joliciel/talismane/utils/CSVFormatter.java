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
package com.joliciel.talismane.utils;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Various utilities for formatting text to CSV and reading out of a CSV file.
 * 
 * @author Assaf Urieli
 *
 */
public class CSVFormatter {
  private DecimalFormat decFormat;
  private DecimalFormat intFormat;
  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private boolean addQuotesAlways = false;
  private String csvSeparator = null;
  private static String globalCsvSeparator = ",";
  private int decimalPlaces = 2;
  private static Locale globalLocale = Locale.US;
  private Locale locale = null;
  private boolean initialized = false;

  private Pattern csvSeparators = null;

  private enum TokenType {
    COMMA,
    QUOTE,
    OTHER
  };

  public CSVFormatter(int decimalPlaces) {
    this();
    this.setDecimalPlaces(decimalPlaces);
  }

  public CSVFormatter() {
  }

  private void initialize() {
    if (!this.initialized) {
      this.updateDecimalFormat();
      this.initialized = true;
    }
  }

  private void updateDecimalFormat() {
    String dfFormat = "0.";
    for (int i = 0; i < decimalPlaces; i++) {
      dfFormat += "0";
    }
    decFormat = (DecimalFormat) DecimalFormat.getNumberInstance(this.getLocale());
    decFormat.applyPattern(dfFormat);
    intFormat = (DecimalFormat) DecimalFormat.getNumberInstance(this.getLocale());
    intFormat.applyPattern("##0");
  }

  /**
   * Format a double for inclusion in a CSV.
   */
  public String format(double number) {
    this.initialize();
    if (addQuotesAlways)
      return "\"" + decFormat.format(number) + "\"" + this.getCsvSeparator();
    return decFormat.format(number) + this.getCsvSeparator();
  }

  /**
   * Format a float for inclusion in a CSV.
   */
  public String format(float number) {
    this.initialize();
    if (addQuotesAlways)
      return "\"" + decFormat.format(number) + "\"" + this.getCsvSeparator();
    return decFormat.format(number) + this.getCsvSeparator();
  }

  /**
   * Format an int for inclusion in a CSV.
   */
  public String format(int number) {
    this.initialize();
    if (addQuotesAlways)
      return "\"" + intFormat.format(number) + "\"" + this.getCsvSeparator();
    return intFormat.format(number) + this.getCsvSeparator();
  }

  /**
   * Format a boolean for inclusion in a CSV.
   */
  public String format(boolean bool) {
    if (addQuotesAlways)
      return "\"" + bool + "\"" + this.getCsvSeparator();
    return bool + this.getCsvSeparator();
  }

  /**
   * Format a String for inclusion in a CSV.
   */
  public String format(String string) {
    int quotePos = string.indexOf('"');
    int commaPos = string.indexOf(this.getCsvSeparator());
    int apostrophePos = string.indexOf('\'');
    if (quotePos >= 0) {
      string = string.replace("\"", "\"\"");
    }
    if (quotePos >= 0 || commaPos >= 0 || apostrophePos >= 0 || addQuotesAlways)
      return "\"" + string + "\"" + this.getCsvSeparator();
    else
      return string + this.getCsvSeparator();

  }

  /**
   * Extract a list of cell contents from a given CSV line.
   * 
   */
  public List<String> getCSVCells(String csvLine) {
    if (csvSeparators == null) {
      csvSeparators = Pattern.compile("((" + this.getCsvSeparator() + ")|\")");
    }
    List<String> cells = new ArrayList<String>();
    Matcher matcher = csvSeparators.matcher(csvLine);
    int currentPos = 0;
    List<String> tokens = new ArrayList<String>();
    while (matcher.find()) {
      if (matcher.start() > currentPos) {
        tokens.add(csvLine.substring(currentPos, matcher.start()));
      }
      tokens.add(csvLine.substring(matcher.start(), matcher.end()));
      currentPos = matcher.end();
    }
    tokens.add(csvLine.substring(currentPos));
    StringBuilder currentCell = new StringBuilder();
    boolean inQuote = false;
    TokenType lastToken = TokenType.OTHER;
    for (String token : tokens) {
      if (token.equals("\"")) {
        inQuote = !inQuote;
        if (lastToken.equals(TokenType.QUOTE)) {
          currentCell.append(token);
          lastToken = TokenType.OTHER;
        } else {
          lastToken = TokenType.QUOTE;
        }
      } else if (token.equals(this.getCsvSeparator())) {
        if (inQuote) {
          currentCell.append(token);
          lastToken = TokenType.OTHER;
        } else {
          cells.add(currentCell.toString().trim());
          currentCell = new StringBuilder();
          lastToken = TokenType.COMMA;
        }
      } else {
        currentCell.append(token);
        lastToken = TokenType.OTHER;
      }
    }
    if (currentCell.length() > 0)
      cells.add(currentCell.toString().trim());
    return cells;
  }

  /**
   * Return the spreadsheet column label corresponding to a certain index.
   */
  public String getColumnLabel(int index) {
    String columnLabel = "";
    int result = index / 26;
    int remainder = index % 26;
    if (result == 0) {
      columnLabel = "" + ALPHABET.charAt(remainder);
    } else {
      columnLabel = "" + ALPHABET.charAt(result - 1) + ALPHABET.charAt(remainder);
    }
    return columnLabel;
  }

  /**
   * Get the zero-based column index corresponding to a particular label, e.g.
   * BB=(26*2)+2-1=54-1=53.
   */
  public int getColumnIndex(String label) {
    int result = 0;
    int multiplier = 1;
    for (int i = label.length() - 1; i >= 0; i--) {
      char c = label.charAt(i);
      if (c >= 'A' && c <= 'Z') {
        int pos = ALPHABET.indexOf(c) + 1;
        result += pos * multiplier;
        multiplier *= 26;
      }
    }
    result -= 1;
    return result;
  }

  /**
   * Get the zero-based row index corresponding to a particular label, e.g. BB19
   * = 18
   */
  public int getRowIndex(String label) {
    int result = 0;
    int multiplier = 1;
    for (int i = label.length() - 1; i >= 0; i--) {
      char c = label.charAt(i);
      if (c >= '0' && c <= '9') {
        int digit = c - '0';
        result += digit * multiplier;
        multiplier *= 10;
      } else {
        break;
      }
    }
    result -= 1;
    return result;
  }

  /**
   * Whether or not to systematically add quotes around all cell contents.
   */
  public void setAddQuotesAlways(boolean addQuotesAlways) {
    this.addQuotesAlways = addQuotesAlways;
  }

  /**
   * The CSV separator to be used (default is a comma).
   */
  public String getCsvSeparator() {
    if (csvSeparator != null) {
      return csvSeparator;
    }
    return globalCsvSeparator;
  }

  public void setCsvSeparator(String separator) {
    csvSeparator = separator;
  }

  public static String getGlobalCsvSeparator() {
    return globalCsvSeparator;
  }

  public static void setGlobalCsvSeparator(String separator) {
    globalCsvSeparator = separator;
  }

  public void setDecimalPlaces(int decimalPlaces) {
    if (this.decimalPlaces != decimalPlaces) {
      this.decimalPlaces = decimalPlaces;
      this.updateDecimalFormat();
    }
  }

  public Locale getLocale() {
    if (this.locale != null)
      return locale;
    return globalLocale;
  }

  public void setLocale(Locale locale) {
    if (!locale.equals(this.locale)) {
      this.locale = locale;
      this.updateDecimalFormat();
    }
  }

  public static Locale getGlobalLocale() {
    return globalLocale;
  }

  public static void setGlobalLocale(Locale globalLocale) {
    CSVFormatter.globalLocale = globalLocale;
  }

  public int getDecimalPlaces() {
    return decimalPlaces;
  }

  public boolean isAddQuotesAlways() {
    return addQuotesAlways;
  }

}
