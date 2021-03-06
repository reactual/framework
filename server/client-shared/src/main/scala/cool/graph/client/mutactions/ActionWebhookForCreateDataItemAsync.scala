package cool.graph.client.mutactions

import com.typesafe.scalalogging.LazyLogging
import cool.graph.Types.Id
import cool.graph._
import cool.graph.client.schema.simple.SimpleSchemaModelObjectTypeBuilder
import cool.graph.deprecated.actions.schemas.CreateSchema
import cool.graph.deprecated.actions.{Event, MutationCallbackSchemaExecutor}
import cool.graph.messagebus.QueuePublisher
import cool.graph.shared.errors.SystemErrors
import cool.graph.shared.models.{Action, Model, Project}
import cool.graph.webhook.Webhook
import scaldi._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class ActionWebhookForCreateDataItemAsync(model: Model, project: Project, nodeId: Id, action: Action, mutationId: Id, requestId: String)(
    implicit inj: Injector)
    extends ActionWebhookMutaction
    with Injectable
    with LazyLogging {

  override def execute: Future[MutactionExecutionResult] = {

    val webhookPublisher = inject[QueuePublisher[Webhook]](identified by "webhookPublisher")

    val payload: Future[Event] =
      new MutationCallbackSchemaExecutor(
        project,
        model,
        new CreateSchema(model = model, modelObjectTypes = new SimpleSchemaModelObjectTypeBuilder(project = project), project = project).build(),
        nodeId,
        action.triggerMutationModel.get.fragment,
        action.handlerWebhook.get.url,
        mutationId
      ).execute

    payload.onSuccess {
      case event: Event =>
        val whPayload = event.payload.map(p => p.compactPrint).getOrElse("")
        webhookPublisher.publish(Webhook(project.id, "", requestId, event.url, whPayload, event.id, Map.empty))
    }

    payload.map(_ => MutactionExecutionSuccess()).recover {
      case x => SystemErrors.UnknownExecutionError(x.getMessage, x.getStackTrace.map(_.toString).mkString(", "))
    }
  }
}
