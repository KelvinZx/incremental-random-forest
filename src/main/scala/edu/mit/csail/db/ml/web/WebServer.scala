package org.apache.spark.ml

import java.awt.Desktop
import java.io.IOException
import java.net.URI

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.ServletException

import org.apache.spark.SparkContext

import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler

import scala.collection.mutable.StringBuilder

import scala.sys.process._


/* 
	This class is responsible for producing a web interface for WahooML. At the moment,
	the default constructer launches Spark's web UI.
	
	Requirements:

		- want to call into a library to run the WebServer
		- user should be able to navigate to app in browser via ip address/port

 */

class WebServer(context: SparkContext)
{
    /* This class serves an HTML page with a simple greeting */
    class GreetingHandler extends AbstractHandler
    {
      @throws(classOf[IOException])
      @throws(classOf[ServletException])
      override def handle(target: String, baseRequest: Request,
                          request: HttpServletRequest,
                          response: HttpServletResponse): Unit =
      {
        response.setContentType("text/html;charset=utf-8")
        response.setStatus(HttpServletResponse.SC_OK)

        baseRequest.setHandled(true)

        // create HTML response
        val sb = new StringBuilder()
        sb.append("<!DOCTYPE html>\n")
        sb.append("<html>\n")
        sb.append("  <head>\n")
        sb.append("    <title>WahooML</title>\n")
		sb.append("    <link href=\"css/bootstrap.css\" rel=\"stylesheet\" media=\"screen\">")
        sb.append("  </head>\n")
        sb.append("  <body>\n")
        sb.append("    <script>\"http://code.jquery.com/jquery.js\"</script>\n")
        sb.append("    <h1>" + context.appName + "</h1>\n")
        sb.append("    <p>id:" + context.applicationId + "</p>\n")
        sb.append("  </body>\n")
        sb.append("</html>")

        // serve HTML page
        val writer = response.getWriter()
        writer.print(sb.toString)
      }
    }
	
	// -variables-
    var port: Int = -1
    var usingSparkUI: Boolean = true

    // -constructor: use custom servlet
    def this(aPort: Int, aContext: SparkContext) =
    {
	    this(aContext)		// call base constructor
        require(aPort >= 0)	// check port is valid

		// set relevant variables
        port = aPort
        usingSparkUI = false
    }
	
	// This method displays a web UI for an active Spark Context
	def display(): Unit =
	{
		val landingPage: String =
		if (usingSparkUI) { "http://localhost:4040" }
		else
		{
			val server = new Server(port)
	    	server.setHandler(new GreetingHandler())
			server.start()
			"http://localhost:" + port
		}

    	// open the web UI
		Desktop.getDesktop().browse(new URI(landingPage))
	}
}