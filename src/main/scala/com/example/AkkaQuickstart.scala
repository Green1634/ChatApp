//#full-example
package com.example


import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.NotUsed
import akka.actor.typed.{ DispatcherSelector, Terminated }
//import com.example.GreeterMain.SayHello

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

//#chatroom-behavior
  object ChatRoom {
    //#chatroom-behavior
    //#chatroom-protocol
    sealed trait RoomCommand
    final case class GetSession(screenName: String, replyTo: ActorRef[SessionEvent]) extends RoomCommand
    //#chatroom-protocol
    //#chatroom-behavior
    private final case class PublishSessionMessage(screenName: String, message: String) extends RoomCommand
    //#chatroom-behavior
    //#chatroom-protocol

    sealed trait SessionEvent
    final case class SessionGranted(handle: ActorRef[PostMessage]) extends SessionEvent
    final case class SessionDenied(reason: String) extends SessionEvent
    final case class MessagePosted(screenName: String, message: String) extends SessionEvent
    

    trait SessionCommand
    final case class PostMessage(message: String) extends SessionCommand
    private final case class NotifyClient(message: MessagePosted) extends SessionCommand
    //#chatroom-protocol
    //#chatroom-behavior

    def apply(): Behavior[RoomCommand] =
      chatRoom(List.empty)

    private def chatRoom(sessions: List[ActorRef[SessionCommand]]): Behavior[RoomCommand] =
      Behaviors.receive { (context, message) =>
        message match {
          case GetSession(screenName, client) =>
            // create a child actor for further interaction with the client
            val ses = context.spawn(
              session(context.self, screenName, client),
              name = URLEncoder.encode(screenName, StandardCharsets.UTF_8.name))
            client tell SessionGranted(ses)
            chatRoom(ses :: sessions)
          case PublishSessionMessage(screenName, message) =>
            val notification = NotifyClient(MessagePosted(screenName, message))
            sessions.foreach(_ tell notification)
            Behaviors.same
        }
      }

    private def session(
        room: ActorRef[PublishSessionMessage],
        screenName: String,
        client: ActorRef[SessionEvent]): Behavior[SessionCommand] =
      Behaviors.receiveMessage {
        case PostMessage(message) =>
          // from client, publish to others via the room
          room tell PublishSessionMessage(screenName, message)
          Behaviors.same
        case NotifyClient(message) =>
          // published from the room
          client tell message
          Behaviors.same
      }
  }
  //#chatroom-behavior

  //#dad actor
  object Dad {
    import ChatRoom._

    def apply(): Behavior[SessionEvent] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage {
          //#chatroom-gabbler
          // We document that the compiler warns about the missing handler for `SessionDenied`
          case SessionDenied(reason) =>
            context.log.info("cannot start chat room session: {}", reason)
            Behaviors.stopped
          //#chatroom-gabbler
          case SessionGranted(handle) =>
            handle tell PostMessage("Hello son how are you doing today")
            Behaviors.same
          case MessagePosted(screenName, message) =>
            if (screenName == "Dad")
              context.log.info("Message has been posted by '{}': {}", screenName, message)
            else
              Behaviors.ignore
            Behaviors.stopped
        }
      }
  }
  //#dad actor

  //#chatroom-gabbler son
  object Son {
    import ChatRoom._

    def apply(): Behavior[SessionEvent] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage {
          //#chatroom-gabbler
          // We document that the compiler warns about the missing handler for `SessionDenied`
          case SessionDenied(reason) =>
            context.log.info("cannot start chat room session: {}", reason)
            Behaviors.stopped
          //#chatroom-gabbler
          case SessionGranted(handle) =>
            handle tell PostMessage("Dad I am doing good")
            Behaviors.same
          case MessagePosted(screenName, message) =>
            if (screenName == "Son")
              context.log.info("Message has been posted by '{}': {}", screenName, message)
            else
              Behaviors.ignore
            Behaviors.stopped
        }
      }
  }//#son actor

  //#chatroom-main
  object Main {
    def apply(): Behavior[NotUsed] =
      Behaviors.setup { context =>
        val chatRoom = context.spawn(ChatRoom(), "chatroom")
        val chatRoom2 = context.spawn(ChatRoom(), "chatroom2")
        val dadRef = context.spawn(Dad(), "dad")
        val sonRef = context.spawn(Son(), "son")
        //val ayomideRef = context.spawn(Gabbler(), "gabbler")
        context.watch(dadRef)
        context.watch(sonRef)
        //context.watch(ayomideRef)
        chatRoom tell ChatRoom.GetSession("Dad", dadRef)
        chatRoom2 tell ChatRoom.GetSession("Son", sonRef)
        //chatRoom tell ChatRoom.GetSession("Ayomide", ayomideRef)

        Behaviors.receiveSignal {
          case (_, Terminated(_)) =>
            Behaviors.stopped
        }
      }

    def main(args: Array[String]): Unit = {
      ActorSystem(Main(), "ChatRoomDemo")
    }

  }
  //#chatroom-main

