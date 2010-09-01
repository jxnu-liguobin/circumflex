package ru.circumflex.web

import ru.circumflex.core._
import util.matching.Regex
import xml.Node

/*!# Routing

Circumflex Web Framework is designed around the *route* concept. A route is an HTTP
method paired with some matching mechanism and attached block.

Each route defines a `Matcher` which describes the conditions a request must satisfy
in order to be matched. If such conditions are met, an attached block is executed
which yields `RouteResponse` to the client.

Routes are declared right inside the body of `RequestRouter` and are executed one-by-one
until first successful match. If no routes match the request, the `404 NOT FOUND` is sent
to the client (unless `onNoMatch` method is overriden in `CircumflexFilter`).

Inside an attached block you can access `MatchResult` object produced by enclosing route.
Match results are stored in `Context`, you can look them up by name.
*/

case class RouteResponse(val body: String)

class Route(matchingMethods: String*) {

  protected def dispatch(matcher: Matcher, response: => RouteResponse): Unit =
    matchingMethods.find(request.method.equalsIgnoreCase(_)) match {
      case Some(_) =>
        matcher.apply() match {
          case None => return
          case Some(matches: Seq[MatchResult]) =>
            matches.foreach(m => ctx.update(m.name, m))
            val r = response.body
            send(text = r)
        }
      case _ =>
    }

  // DSL-like syntax (`get("/") = { ... }`)
  def update(matcher: Matcher, response: => RouteResponse): Unit =
    dispatch(matcher, response)

}

class RequestRouter(val prefix: String = "") {

  implicit def string2response(str: String): RouteResponse =
    new RouteResponse(str)
  implicit def xml2response(xml: Node): RouteResponse =
    new RouteResponse("<?xml version=\"1.0\"?>\n" + xml.toString)
  implicit def router2response(router: RequestRouter): RouteResponse = sendError(404)

  implicit def string2uriMatcher(str: String): RegexMatcher =
    new RegexMatcher("uri", request.uri, prefix + str)
  implicit def regex2uriMatcher(regex: Regex): RegexMatcher =
    new RegexMatcher("uri", request.uri, new Regex(prefix + regex.toString))

  // ### Routes

  val get = new Route("get")
  val getOrPost = new Route("get", "post")
  val getOrHead = new Route("get", "head")
  val post = new Route("post")
  val put = new Route("put")
  val patch = new Route("patch")
  val delete = new Route("delete")
  val head = new Route("head")
  val options = new Route("options")
  val any = new Route("get", "post", "put", "patch" , "delete", "head", "options")

  def uri: MatchResult = ctx.get("uri") match {
    case Some(m: MatchResult) => m
    case None => new MatchResult("uri", "splat" -> request.uri)
  }

}