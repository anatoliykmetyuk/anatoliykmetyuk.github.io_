When programming in a purely functional style, we aim to reify side effects into data structures called TODO effect types. An effect type you are using should be the same throughout the entire application so that different parts of the application are composable.

When having multiple side effects and a single effect type to express them, the problem arises on how to convert the former to the latter conveniently. Also, when working with functional libraries, we need to translate from their effect systems (TODO foreign effect systems) to the one used by our application. We need such translations because purely functional libraries – e.g. TODO Typelevel stack – employ effect systems that are usually different from the one used by our application. 

In this article, I would like to describe my approach for working with these conversions. We will use an analogy of how operating systems use file extensions to classify files by their type. We will apply the analogy to an example of HTTP handlers for a purely functional web server.
Example
Let us look at a problem of request handling by a web server. We need to define a handler method that accepts request body and performs specific actions based on this body. Precisely, the body is JSON. It contains the name of a file on the server. The handler must read the file and send its contents back to the requesting user. For simplicity, sending the content to the end user is modelled as command line output, and we only consider the request body, not an entire HTTP request. The result type of the handler should be an effect type encoding the actions described above. How should we define this effect type?

Effect System for the Example
For web servers, non-blocking execution and error reporting are essential. Therefore, we are using the following type as an effect type:

To do ef

We are using the TODO EitherT type to combine the effect types TODO IO and TODO Either. TODO Either is implied by TODO EitherT, and the latter is the monad transformer for TODO Either. TODO IO is responsible for asynchrony (hence non-blocking computations), and TODO Either – for error reporting. In TODO Either, we represent errors as TODO Strings under TODO Left.

We define the effect system in the package object so that it is accessible from the entire application. An TODO effect system is the effect type we use and the utility methods and classes we employ to work with it throughout the code.

What utility methods and classes do we need in our effect system? To define them, let us look at the side effects we need to deal with in our request handler.
Handler Side Effects
The signature of the handler is as follows.

To do signature of request handler without the body

The handler receives a JSON body and performs the following business logic steps:

TODO make list
Parse the body using Circe, the JSON library for Scala.
Log the parsing results to the console.
Read the file name from JSON. The name is stored under the TODO file key.
Read the file contents as a string.
Output the file contents to the command line (the output effect simulates sending the contents as a response to the end user).

In the above business logic, we encounter the following side effects and foreign effect types: 

To do make list
Foreign Effect: TODO Either. In Circe, unsafe operations return the result under the effect type TODO Either[E <: io.circe.Error, A]. TODO io.circe.Error is an exception type, and TODO A is a result type. We encounter this effect type when parsing and accessing JSON, points (1), (3) in the handler algorithm above.
Side Effect: Suspension. We need to lift operations like the output to the command line into the IO context. We encounter this side effect when printing to the command line, points (2), (5) above. 
Side Effect: throwing of exceptions. We need to handle OOP-style computations that may throw exceptions. We need to capture these exceptions under the TODO Ef type as errors. We encounter this side effect in point (4) above.

It is necessary to define how we are going to translate each of these side effects and foreign effect types into our effect type TODO Ef. We need these translations to be declarative and easy to use so that they don't distract us from writing the business logic. Precisely, for each side effect and foreign effect type, we need a translation formalism that captures their meaning into TODO Ef, along with the result they compute. How should we define such translation formalisms?

Effect Extensions
Operating systems distinguish between different file types using extensions in the files' names - TODO .scala, .txt, .png etc. Based on these extensions, an operating system knows which software to use to open the file. The file extensions technique is a formalism – a dot followed by a descriptive extension name. This formalism is used to declare the types of the files.

Effect Extensions pattern applies the formalism of file extensions to translate different side effects and foreign effect types to the effect type of the application we are writing. Here is how the request handler looks under the effect extensions pattern:

To do a request handler body together with imports

Every side effect and foreign effect type we have identified in the TODO Handler Side Effects subsection has its own extension. The semantics of each extension with respect to the expression it is called on is as follows:

TODO make a list
TODO .etr – lifts TODO Either to TODO Ef.
TODO .sus – wraps the (by-name) expression into an TODO IO context, then lifts this TODO IO to TODO Ef.
TODO .exn – executes the expression, catches all exceptions that occurred as part of this execution. These exceptions are converted into a TODO Left if they occur, which is lifted to TODO Ef.

All of the extensions produce an TODO Ef from the expressions they are called on.

How do we implement the effect extensions as a part of our effect system?
Implementation of the pattern
Strategy
We are using the Rich Wrapper pattern to inject effect extensions into the expression types we want to use these extensions on.

To do, Which rapperS for either and delayed

We also define the capability to evaluate the TODO Ef type synchronously.

To do reach a
Entire Pattern
The entire pattern defines the following two components:

TODO list
An Effect Type we are going to use throughout the entire application.
Effect Extensions, defined as rich wrappers.
We define the pattern in the package object of the application. Here is how it looks in its entirety:

TODO entire pattern

The pattern worked for me for a broad range of applications, but for more complicated cases, you may want to improve it – see TODO Directions for Improvement section.

Running the Example
Successful Run
Let us assume that we have a file named TODO foo.txt in the root of the project with the content of TODO "Hello World!". We can achieve a successful execution of the handler as follows.

To do successful around

To do screenshot of successful on

Failure: Circe
We can feed a malformed JSON string to the handler to simulate a failure of the body parsing stage:

To do CSS parser failed

Screenshot of the above example

We can also have a correctly formatted JSON which does now have the TODO file key the handler needs:

To do key via example

To do key file example screenshot
Failure: File Input
Finally, we can simulate the failure to read a file by providing a name of a file that does not exist.

To-do file failure

To do file for example

Previous Work
In my previous projects, I used the pattern without rich wrappers as follows:

To-do example of request handler without her the traffic's

I defined the effect extension methods as ordinary methods in the TODO package object. However, the approach as in the code sample above forced me to use curly braces a lot. Usage of braces decreases the readability of code.

The advantage of the effect extension pattern with rich wrappers is that the formalism required to specify the side effect types is localised at the end of the expressions and does not wrap them, which increases readability.
Directions for Improvement
A big brother of the rich wrapper pattern is the type class pattern. If rich wrappers are not enough for your project, consider specifying effect extensions as type classes.

The motivation for placing the entire pattern into the TODO package object is for it to be accessible from the entire application. The TODO cats library solves the accessibility problem by stuffing all the functionality into a single object:

To do example of cats in places in ports Newport

If the package object approach does not work for you, consider following in footsteps of TODO cats.
In the file extension world, we have extensions that are widely recognised – TODO .scala, .png, .txt. In the type class world, we have libraries of type classes like Cats or ScalaZ. Can we have a library of widely recognised effect type extensions? What problem might rise a need for such a library? Can you think of possible applications? Share your thoughts in the comments!


