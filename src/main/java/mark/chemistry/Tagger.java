/*
 *  Copyright (c) 2004-2016, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 3, June 2007 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 */
package mark.chemistry;

import java.io.BufferedReader;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.Gate;
import gate.LanguageAnalyser;
import gate.ProcessingResource;
import gate.Resource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.BomStrippingInputStreamReader;
import gate.util.InvalidOffsetException;

/**
 * A tagger for chemical elements and compounds.
 */
@CreoleResource(name="Chemistry Tagger", comment="A tagger for chemical names.", helpURL="http://gate.ac.uk/userguide/sec:parsers:chemistrytagger", icon="chemistry")
public class Tagger extends AbstractLanguageAnalyser implements
                                                    ProcessingResource,
                                                    Serializable {

  private static final long serialVersionUID = -2754855608422187746L;

  private LanguageAnalyser gazc = null;

  private LanguageAnalyser gazo = null;

  private LanguageAnalyser net = null;

  private String annotationSetName = null;

  // // Init parameters ////
  /**
   * The URL of the gazetteer lists definition for spotting elements as
   * part of compounds.
   */
  private URL compoundListsURL;

  @CreoleParameter(comment="The gazetteer lists definition for spotting element symbols as part of compounds", defaultValue="resources/compound.def")
  public void setCompoundListsURL(URL newValue) {
    compoundListsURL = newValue;
  }

  public URL getCompoundListsURL() {
    return compoundListsURL;
  }

  @RunTime
  @Optional
  @CreoleParameter(comment="The annotation set to use")
  public void setAnnotationSetName(String name) {
    annotationSetName = name;
  }

  public String getAnnotationSetName() {
    return annotationSetName;
  }

  /**
   * The URL of the gazetteer lists definition for spotting elements on
   * their own.
   */
  private URL elementListsURL;

  @CreoleParameter(comment="The gazetteer lists definition for spotting element symbols and names alone", defaultValue="resources/element.def")
  public void setElementListsURL(URL newValue) {
    elementListsURL = newValue;
  }

  public URL getElementListsURL() {
    return elementListsURL;
  }

  /**
   * URL of the JAPE grammar.
   */
  private URL transducerGrammarURL;

  @CreoleParameter(comment="The JAPE grammar", defaultValue="resources/main.jape")
  public void setTransducerGrammarURL(URL newValue) {
    transducerGrammarURL = newValue;
  }

  public URL getTransducerGrammarURL() {
    return transducerGrammarURL;
  }

  private Boolean removeElements;

  @RunTime
  @CreoleParameter(comment="Remove elements which are part of a larger compound or ion", defaultValue="true")
  public void setRemoveElements(Boolean newValue) {
    removeElements = newValue;
  }

  public Boolean getRemoveElements() {
    return removeElements;
  }

  private URL elementMapURL;

  @CreoleParameter(comment="File which contains the mapping between element symbols and names",defaultValue="resources/element_map.txt")
  public void setElementMapURL(URL newValue) {
    elementMapURL = newValue;
  }

  public URL getElementMapURL() {
    return elementMapURL;
  }

  private List<String> elementSymbol, elementName;

  /**
   * Create the tagger by creating the various gazetteers and JAPE
   * transducers it uses.
   */
  @Override
  public Resource init() throws ResourceInstantiationException {
    // sanity check parameters
    if(compoundListsURL == null) {
      throw new ResourceInstantiationException(
              "Compound lists URL must be specified");
    }
    if(elementListsURL == null) {
      throw new ResourceInstantiationException(
              "Element lists URL must be specified");
    }
    if(transducerGrammarURL == null) {
      throw new ResourceInstantiationException(
              "Transducer grammar URL must be specified");
    }
    elementSymbol = new ArrayList<String>();
    elementName = new ArrayList<String>();
    try (BufferedReader in = new BomStrippingInputStreamReader(
              elementMapURL.openStream());) {      
      String symbol = in.readLine();
      while(symbol != null) {
        symbol = symbol.trim();
        String name = in.readLine().trim();
        elementSymbol.add(symbol);
        elementName.add(name.toLowerCase());
        symbol = in.readLine();
      }
    }
    catch(Exception e) {
      throw new ResourceInstantiationException("Malformed element map file");
    }
    FeatureMap hidden = Factory.newFeatureMap();
    Gate.setHiddenAttribute(hidden, true);

    FeatureMap params = Factory.newFeatureMap();
    params.put("listsURL", compoundListsURL);
    params.put("wholeWordsOnly", Boolean.FALSE);
    if(gazc == null) {
      gazc = (LanguageAnalyser)Factory.createResource(
              "gate.creole.gazetteer.DefaultGazetteer", params, hidden);
    }
    else {
      gazc.setParameterValues(params);
      gazc.reInit();
    }

    params = Factory.newFeatureMap();
    params.put("listsURL", elementListsURL);
    if(gazo == null) {
      gazo = (LanguageAnalyser)Factory.createResource(
              "gate.creole.gazetteer.DefaultGazetteer", params, hidden);
    }
    else {
      gazo.setParameterValues(params);
      gazo.reInit();
    }

    params = Factory.newFeatureMap();
    params.put("grammarURL", transducerGrammarURL);
    if(net == null) {
      net = (LanguageAnalyser)Factory.createResource("gate.creole.Transducer",
              params, hidden);
    }
    else {
      net.setParameterValues(params);
      net.reInit();
    }
    
    return this;
  }

  public void cleanup() {
    Factory.deleteResource(gazc);
    Factory.deleteResource(gazo);
    Factory.deleteResource(net);
  }

  @Override
  public void execute() throws ExecutionException {

    Document doc = getDocument();

    try {
      gazc.setDocument(doc);
      gazc.setParameterValue("annotationSetName", annotationSetName);

      gazo.setDocument(doc);
      gazo.setParameterValue("annotationSetName", annotationSetName);

      net.setDocument(doc);
      net.setParameterValue("inputASName", annotationSetName);
      net.setParameterValue("outputASName", annotationSetName);
    }
    catch(ResourceInstantiationException rie) {
      throw new ExecutionException(rie);
    }

    try {
      gazc.execute();
      gazo.execute();
      net.execute();
      // This lot used to be in the clean.jape file but it was slowing
      // things down a lot as what I really wanted would have required
      // the brill style to do what it is meant to do.

      AnnotationSet docAS = doc.getAnnotations(annotationSetName);

      FeatureMap params = Factory.newFeatureMap();
      AnnotationSet temp = docAS.get("NotACompound", params);
      if(temp != null) docAS.removeAll(temp);
      params.put("majorType", "CTelement");
      temp = docAS.get("Lookup", params);
      if(temp != null) docAS.removeAll(temp);
      params.put("majorType", "chemTaggerSymbols");
      temp = docAS.get("Lookup", params);
      if(temp != null) docAS.removeAll(temp);
      if(removeElements.booleanValue()) {
        params = Factory.newFeatureMap();
        AnnotationSet compounds = docAS.get("ChemicalCompound", params);
        if(compounds != null) {
          Iterator<Annotation> cit = compounds.iterator();
          while(cit.hasNext()) {
            Annotation compound = cit.next();
            AnnotationSet elements = docAS.get("ChemicalElement", compound
                    .getStartNode().getOffset(), compound.getEndNode()
                    .getOffset());
            if(elements != null) {
              docAS.removeAll(elements);
            }
          }
        }
      }
      params = Factory.newFeatureMap();
      AnnotationSet elements = docAS.get("ChemicalElement", params);
      if(elements != null) {
        Iterator<Annotation> eit = elements.iterator();
        while(eit.hasNext()) {
          Annotation element = eit.next();
          try {
            String span = doc
                    .getContent()
                    .getContent(element.getStartNode().getOffset(),
                            element.getEndNode().getOffset()).toString();
            FeatureMap feats = element.getFeatures();
            String type = (String)feats.get("kind");
            if(type.equalsIgnoreCase("symbol")) {
              feats.put("symbol", span);
              int index = elementSymbol.indexOf(span);
              if(index != -1) {
                feats.put("name", elementName.get(index));
              }
              feats.put("uri",
                      "http://www.daml.org/2003/01/periodictable/PeriodicTable.owl#"
                              + span);
            }
            else if(type.equalsIgnoreCase("name")) {
              feats.put("name", span);
              int index = elementName.indexOf(span.toLowerCase());
              if(index != -1) {
                String symbol = elementSymbol.get(index);
                feats.put("symbol", symbol);
                feats.put("uri",
                        "http://www.daml.org/2003/01/periodictable/PeriodicTable.owl#"
                                + symbol);
              }
            }
          }
          catch(InvalidOffsetException ioe) {
          }
        }
      }
    }
    finally {
      // make sure document references are released after use
      gazc.setDocument(null);
      gazo.setDocument(null);
      net.setDocument(null);
    }
  }
}
