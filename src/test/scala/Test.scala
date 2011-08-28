package org.w3.readwriteweb

import org.specs._
import java.net.URL
import unfiltered.response._
import unfiltered.request._
import dispatch._
import java.io._

import com.codecommit.antixml._

import com.hp.hpl.jena.rdf.model._
import com.hp.hpl.jena.query._
import com.hp.hpl.jena.update._

import org.w3.readwriteweb.util._
import org.w3.readwriteweb.utiltest._

object ReadWriteWebSpec extends Specification with unfiltered.spec.jetty.Served {

  val base = new File("src/main/resources")
  val joe = host / "2007/wiki/people/JoeLambda"
  val baseURI = "%s%s" format (joe.host, joe.path)
  val joeOnDisk = new File(base, "2007/wiki/people/JoeLambda")
  
  doBeforeSpec {
    if (joeOnDisk.exists) joeOnDisk.delete()    
  }
  
  doAfterSpec {
//    if (joeOnDisk.exists) joeOnDisk.delete()
  }
  
  def setup = { _.filter(new ReadWriteWeb(base).read) }
    
  val joeRDF =
"""
<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"> 
  <foaf:Person rdf:about="#JL" xmlns:foaf="http://xmlns.com/foaf/0.1/">
    <foaf:name>Joe Lambda</foaf:name>
    <foaf:homepage rdf:resource="/2007/wiki/people/JoeLambda" />
  </foaf:Person>
</rdf:RDF>
"""
  
  val initialModel = modelFromString(joeRDF, baseURI)

//        <foaf:openid rdf:resource="/2007/wiki/people/JoeLambda" />
//    <foaf:img rdf:resource="/2007/wiki/people/JoeLambda/images/me.jpg" />

  "PUTing an RDF document on Joe's URI (which does not exist yet)" should {
    "return a 201" in {
      val httpCode:Int = Http(joe.put(joeRDF) get_statusCode)
      httpCode must_== 201
    }
    "create a document on disk" in {
      joeOnDisk must exist
    }
  }
  
  "Joe's URI" should {
    "now exist and be isomorphic with the original document" in {
      val (statusCode, via, model) = Http(joe >++ { req => (req.get_statusCode,
                                                            req.get_header("MS-Author-Via"),
                                                            req as_model(baseURI))
                                                  } )
      statusCode must_== 200
      via must_== "SPARQL"
      model must beIsomorphicWith (initialModel)
    }
  }
  
  val insertQuery =
"""
PREFIX foaf: <http://xmlns.com/foaf/0.1/>
INSERT DATA { </2007/wiki/people/JoeLambda#JL> foaf:openid </2007/wiki/people/JoeLambda> }
"""
  
  "POSTing an INSERT query on Joe's URI (which does not exist yet)" should {
    "succeed" in {
      val httpCode:Int = Http(joe.post(insertQuery) get_statusCode)
      httpCode must_== 200
    }
    "produce a graph with one more triple than the original one" in {
      val model = Http(joe as_model(baseURI))
      model.size must_== (initialModel.size + 1)
    }
  }
  
  val diffRDF =
"""
<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"> 
  <foaf:Person rdf:about="#JL" xmlns:foaf="http://xmlns.com/foaf/0.1/">
    <foaf:img rdf:resource="/2007/wiki/people/JoeLambda/images/me.jpg" />
  </foaf:Person>
</rdf:RDF>
"""

  val expectedFinalModel = modelFromString(
"""
<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"> 
  <foaf:Person rdf:about="#JL" xmlns:foaf="http://xmlns.com/foaf/0.1/">
    <foaf:name>Joe Lambda</foaf:name>
    <foaf:homepage rdf:resource="/2007/wiki/people/JoeLambda" />
    <foaf:openid rdf:resource="/2007/wiki/people/JoeLambda" />
    <foaf:img rdf:resource="/2007/wiki/people/JoeLambda/images/me.jpg" />
  </foaf:Person>
</rdf:RDF>
""", baseURI)

  "POSTing an RDF documenton Joe's URI" should {
    "succeed" in {
      val httpCode:Int = Http(joe.post(diffRDF) get_statusCode)
      httpCode must_== 200
    }
    "append the diff graph" in {
      val model = Http(joe as_model(baseURI))
      model must beIsomorphicWith (expectedFinalModel)
    }
  }
  
  val selectFoafName =
"""
PREFIX foaf: <http://xmlns.com/foaf/0.1/>
SELECT ?name WHERE { [] foaf:name ?name }
"""
  
  """POSTing "SELECT ?name WHERE { [] foaf:name ?name }" to Joe's URI""" should {
    "return Joe's name" in {
      val resultSet = Http(joe.post(selectFoafName) >- { body => ResultSetFactory.fromXML(body) } )
      resultSet.next().getLiteral("name").getString must_== "Joe Lambda"
    }
  }
  
  val askFoafName =
"""
PREFIX foaf: <http://xmlns.com/foaf/0.1/>
ASK { [] foaf:name ?name }
"""
  
  """POSTing "ASK ?name WHERE { [] foaf:name ?name }" to Joe's URI""" should {
    "return true" in {
      val result:Boolean = Http(joe.post(askFoafName) >~ { s => (XML.fromSource(s) \ "boolean" \ text).head.toBoolean } )
      result must_== true
    }
  }
    
}
