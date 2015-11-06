package edu.mit.csail.db.ml

import org.apache.spark.ml.{Estimator, Model}
import org.apache.spark.ml.classification.LogisticRegressionModel
import org.apache.spark.sql.DataFrame

import com.mongodb.casbah.Imports._

/**
 * The model database. Currently, it is simply a Map which maps ModelSpec to Model.
 */
class ModelDb {
  /**
   * Set up database connection
   */
  val mongoClient = MongoClient()
  val modelCollection = mongoClient("wahooml")("models")

  private val cachedModels = collection.mutable.Map[Any, Any]()

  /**
   * Store this model in the database.
   * @param spec - The specification. Since this is a key, make sure it implements equals() and
   *             hashCode().
   * @param model - The model to store with the given key.
   * @tparam M - The type of the model being stored. For example, LogisticRegressionModel.
   */
  def cache[M <: Model[M]](spec: ModelSpec[M], model: M, dataset: DataFrame): Unit = {
    spec match {
      case lrspec: LogisticRegressionSpec => {
        val lrmodel: LogisticRegressionModel = model.asInstanceOf[LogisticRegressionModel]
        val modelObj: MongoDBObject = DBObject(
          "uid" -> lrmodel.uid,
          "weights" -> lrmodel.weights.toArray,
          "intercept" -> lrmodel.intercept,
          "dataframe" -> "df1",
          "modelspec" -> DBObject(
            "type" -> "LogisticRegressionModel",
            "features" -> lrspec.features,
            "regParam" -> lrspec.regParam,
            "maxIter" -> lrspec.maxIter
          )
        )
        println("wahoo obj: " + modelObj)
        modelCollection += modelObj
        println("model collection: " + modelCollection.find())
      }
      // TODO add more types
    }
    cachedModels.put(spec, model)
  }

  /**
   * Check whether this model specification has a model that has been cached.
   * @param spec - The specification, make sure it implements equals() and hashCode().
   * @tparam M - The type of the model. For example, LogisticRegressionModel.
   * @return Whether the cache contains a model with the given specification.
   */
  def contains[M <: Model[M]](spec: ModelSpec[M]) = cachedModels.contains(spec)

  /**
   * Fetch the model with the given specification, or execute a function to generate a model if the
   * result is not found.
   * @param spec - The specification to look up, make sure it implements equals() and hashCode().
   * @param orElse - The function to execute if no model is found with the given specification. It
   *               should return a new model.
   * @tparam M - The type of the model. For example, LogisticRegressionModel.
   * @return The model fetched from the database, or the result of orElse.
   */
  def getOrElse[M <: Model[M]](spec: ModelSpec[M])(orElse: ()=> M): M =
    if (contains[M](spec) && cachedModels(spec).isInstanceOf[Model[M]])
      cachedModels(spec).asInstanceOf[M] else orElse()
}

/**
 * Augments a class with a model database. Call the setDb method and ensure each object points to the
 * same model database.
 */
trait HasModelDb {
  private var modelDb = new ModelDb()
  def setDb(db: ModelDb) = modelDb = db
  def getDb: ModelDb = modelDb
}

/**
 * Augment a class to give it the ability to check for a model in the model database before it tries
 * training a new one.
 * @tparam M - The type of the model. For example, LogisticRegressionModel.
 */
trait CanCache[M <: Model[M]] extends Estimator[M] with HasModelDb {
  /**
   * Generate a ModelSpec given the dataset.
   * @param dataset - The dataset.
   * @return The model specification.
   */
  def modelSpec(dataset: DataFrame): ModelSpec[M]

  /**
   * Smarter fit method which checks the cache before it trains. If the model is not in the cache,
   * train it. Otherwise, returned the cached model.
   * @param dataset - The dataset to train on.
   * @return The trained model.
   */
  abstract override def fit(dataset: DataFrame): M =
    super.getDb.getOrElse[M](modelSpec(dataset))(() => {
      val model = super.fit(dataset)
      super.getDb.cache[M](modelSpec(dataset), model, dataset)
      model
    })
}
