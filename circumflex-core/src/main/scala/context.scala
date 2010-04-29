package ru.circumflex.core

import java.io.File
import java.util.{Locale, ResourceBundle}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import javax.activation.MimetypesFileTypeMap
import org.apache.commons.io.FilenameUtils._

class CircumflexContext(val request: HttpServletRequest,
                        val response: HttpServletResponse,
                        val filter: AbstractCircumflexFilter)
    extends HashModel {

  /**
   * A helper for getting and setting response headers in a DSL-like way.
   */
  object header extends HashModel {

    def get(key: String) = apply(key)

    def apply(name: String): Option[String] = request.getHeader(name)

    def update(name: String, value: String) { response.setHeader(name, value) }

    def update(name: String, value: Long) { response.setDateHeader(name, value) }

    def update(name: String, value: java.util.Date) { update(name, value.getTime) }

  }

  /**
   * A helper for getting and setting session-scope attributes.
   */
  object session extends HashModel {

    def get(key: String) = apply(key)

    def apply(name: String): Option[Any] = request.getSession.getAttribute(name)

    def update(name: String, value: Any) = request.getSession.setAttribute(name, value)

  }

  /**
   * A helper for setting flashes. Flashes provide a way to pass temporary objects between requests.
   */
  object flash extends HashModel {

    private val _key = "cx.flash"

    def get(key: String) = apply(key)

    def apply(key: String): Option[Any] = {
      val flashMap = session.getOrElse(_key, MutableMap[String, Any]())
      flashMap.get(key) map { value => {
        session(_key) = flashMap - key
        value
      }}
    }

    def update(key: String, value: Any) {
      val flashMap = session.getOrElse(_key, MutableMap[String, Any]())
      session(_key) = flashMap + (key -> value)
    }

  }

  // ### Content type

  protected var _contentType: String = null

  def contentType: Option[String] = _contentType

  def contentType_=(value: String) { _contentType = value }

  // ### Status code

  var statusCode: Int = 200

  // ### Method

  def method: String = getOrElse('_method, request.getMethod)

  // ### Request parameters

  private val _params = MutableMap[String, Any](
    "header" -> header,
    "session" -> session,
    "flash" -> flash
  )
  
  def get(key: String): Option[Any] = _params.get(key) match {
    case Some(value) if (value != null) => value
    case _ => request.getParameter(key)
  }
  
  def getString(key: String): Option[String] = get(key) map { _.toString }

  def apply(key: String): Option[Any] = get(key)

  def update(key: String, value: Any) { _params += key -> value }
  def +=(pair: (String, Any)) { _params += pair }

  // ### Request matching

  private[core] var _matches: Map[String, Match] = _

  def getMatch(key: String): Option[Match] = _matches.get(key)

}

object CircumflexContext {
  private val threadLocalContext = new ThreadLocal[CircumflexContext]

  def context = threadLocalContext.get

  def isOk = context != null

  def init(req: HttpServletRequest,
           res: HttpServletResponse,
           filter: AbstractCircumflexFilter) {
    threadLocalContext.set(new CircumflexContext(req, res, filter))
    try {
      context('msg) = Circumflex.msg(req.getLocale)
    } catch {
      case e => cxLog.debug("Could not instantiate context messages.", e)
    }
  }

  def destroy() = threadLocalContext.set(null)
}

object Circumflex { // TODO: move all into package object?

  // ## Configuration

  private val _cfg = MutableMap[String, Any]()
  val cfg = new ConfigurationHelper

  // should filter process request?
  val f: HttpServletRequest => Boolean = r => !r.getRequestURI.toLowerCase.matches("/public/.*")
  _cfg += "cx.process_?" -> f
  // webapp root
  _cfg += "cx.root" -> "src/main/webapp"
  // static files directory (relative to webapp root)
  _cfg += "cx.public" -> "public"
  // resource bundle for messages
  _cfg += "cx.messages" -> "Messages"

  val classLoader: ClassLoader = cfg("cx.classLoader") match {
    case Some(cld: ClassLoader) => cld
    case _ => Thread.currentThread.getContextClassLoader
  }

  def loadClass[C](name: String): Class[C] =
    Class.forName(name, true, classLoader).asInstanceOf[Class[C]]

  val webappRoot: File = cfg("cx.root") match {
    case Some(s: String) => new File(separatorsToSystem(s))
    case _ => throw new CircumflexException("'cx.root' not configured.")
  }

  val publicRoot = cfg("cx.public") match {
    case Some(s: String) => new File(webappRoot, separatorsToSystem(s))
    case _ => throw new CircumflexException("'cx.public' not configured.")
  }

  val XSendFileHeader: XSendFileHeader = cfg("cx.XSendFileHeader") match {
    case Some(h: XSendFileHeader) => h
    case Some(c: Class[XSendFileHeader]) => c.newInstance
    case Some(s: String) => loadClass[XSendFileHeader](s)
        .newInstance
    case _ => DefaultXSendFileHeader
  }

  // Read configuration from `cx.properties` file by default
  try {
    val bundle = ResourceBundle.getBundle("cx", Locale.getDefault, classLoader)
    val keys = bundle.getKeys
    while (keys.hasMoreElements) {
      val k = keys.nextElement
      cfg(k) = bundle.getString(k)
    }
  } catch {
    case _ => cxLog.warn("Could not read configuration parameters from cx.properties.")
  }

  /**
   * A simple helper for DSL-like configurations.
   */
  class ConfigurationHelper {
    def apply(key: String): Option[Any] = _cfg.get(key)
    def apply(key: String, default: Any): Any = _cfg.getOrElse(key, default)
    def update(key: String, value: Any): Unit =
      _cfg += key -> value
  }

  // ## Messages

  def msg(locale: Locale): Messages = cfg("cx.messages") match {
    case Some(s: String) => new Messages(s, locale)
    case _ => throw new CircumflexException("'cx.messages' not configured.")
  }

  // ## Miscellaneous

  def mimeTypesMap = new MimetypesFileTypeMap()

}

class CircumflexException(msg: String, cause: Throwable)
    extends Exception(msg, cause) {
  def this(msg: String) = this(msg, null)
  def this(cause: Throwable) = this(null, cause)
}