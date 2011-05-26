package au.org.ala.biocache
/**
 * These classes represent caches of data sourced from other components
 * maintained within the biocache for performance reasons. These
 * components
 */
import au.org.ala.data.model.LinnaeanRankClassification
import au.org.ala.util.ReflectBean
import au.org.ala.checklist.lucene.model.NameSearchResult
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import au.org.ala.checklist.lucene.{CBIndexSearch, HomonymException, SearchResultException}

/**
 * A DAO for accessing classification information in the cache. If the
 * value does not exist in the cache the name matching API is called.
 *
 * The cache will store a classification object for names that match. If the
 * name causes a homonym exeception or is not found the ErrorCode is stored.
 *
 * @author Natasha Carter
 *
 */
object ClassificationDAO {

  private val columnFamily ="namecl"
 // private val cachedValues = new java.util.Hashtable[LinnaeanRankClassification, Classification]
  private val lru = new org.apache.commons.collections.map.LRUMap(10000)
//  private val lru = new ConcurrentLinkedHashMap.Builder[String, Option[NameSearchResult]]()
//    .maximumWeightedCapacity(10000)
//    .build();

  private val lock : AnyRef = new Object()

  private val nameIndex = Config.getInstance(classOf[CBIndexSearch]).asInstanceOf[CBIndexSearch]

  /**
   * Uses a LRU cache
   */
  def getByHashLRU(cl:Classification) : Option[NameSearchResult] = {

    val hash = {
        Array(cl.kingdom,cl.phylum,cl.phylum,cl.classs,cl.order,cl.family,cl.genus,cl.species,cl.specificEpithet,
            cl.subspecies,cl.infraspecificEpithet,cl.scientificName).reduceLeft(_+"|"+_)
    }
    val cachedObject = lock.synchronized { lru.get(hash) }

    if(cachedObject!=null){
       cachedObject.asInstanceOf[Option[NameSearchResult]]
    } else {
        //use the lucene indexes
        val lrc = new LinnaeanRankClassification(
          cl.kingdom,
          cl.phylum,
          cl.classs,
          cl.order,
          cl.family,
          cl.genus,
          cl.species,
          cl.specificEpithet,
          cl.subspecies,
          cl.infraspecificEpithet,
          cl.scientificName)

        val nsr = nameIndex.searchForRecord(lrc, true)

        if(nsr!=null){
            val result = Some(nsr)
            lock.synchronized { lru.put(hash, result) }
            result
        } else {
            val result = None
            lock.synchronized { lru.put(hash, result) }
            result
        }
      }
  }
}

/**
 * A DAO for accessing taxon profile information by GUID.
 * 
 * This should provide an abstraction layer, that (eventually) handles
 * "timeToLive" style functionality that invalidates values in the cache
 * and retrieves the latest values.
 */
object TaxonProfileDAO {

  private val columnFamily = "taxon"
  private val lru = new org.apache.commons.collections.map.LRUMap(10000)
  private val lock : AnyRef = new Object()
  private val persistenceManager = Config.getInstance(classOf[PersistenceManager]).asInstanceOf[PersistenceManager]
//  private val lru = new ConcurrentLinkedHashMap.Builder[String, Option[TaxonProfile]]()
//      .maximumWeightedCapacity(10000)
//      .build();

  /**
   * Retrieve the profile by the taxon concept's GUID
   */
  def createTaxonProfile(map: Option[Map[String, String]]): TaxonProfile = {
      var taxonProfile = new TaxonProfile
      map.get.foreach(keyValue => {
          keyValue._1 match {
              case "guid" => taxonProfile.guid = keyValue._2
              case "scientificName" => taxonProfile.scientificName = keyValue._2
              case "commonName" => taxonProfile.commonName = keyValue._2
              case "rankString" => taxonProfile.rankString = keyValue._2
              case "habitats" => {
                  if (keyValue._2 != null && keyValue._2.size > 0) {
                      taxonProfile.habitats = keyValue._2.split(",")
                  }
              }
              case "left" => taxonProfile.left = keyValue._2
              case "right" => taxonProfile.right = keyValue._2
              case "sensitive" => {
                  if (keyValue._2 != null && keyValue._2.size > 0) {
                      taxonProfile.sensitive = Json.toArray(keyValue._2, classOf[SensitiveSpecies].asInstanceOf[java.lang.Class[AnyRef]]).asInstanceOf[Array[SensitiveSpecies]]
                  }
              }
              case _ => //ignore
          }
      })
      taxonProfile
  }

  def getByGuid(guid:String) : Option[TaxonProfile] = {

    if(guid==null || guid.isEmpty) return None

    val taxonProfile = {

        val cachedObject = lock.synchronized { lru.get(guid) }
        if(cachedObject==null){
            val map = persistenceManager.get(guid,columnFamily)
            if(!map.isEmpty){
              val result = Some(createTaxonProfile(map))
              lock.synchronized { lru.put(guid,result) }
              result
            } else {
              lock.synchronized { lru.put(guid,None) }
              None
            }
        } else {
          cachedObject
        }
    }
    taxonProfile.asInstanceOf[Option[TaxonProfile]]
  }

  /**
   * Persist the taxon profile.
   */
  def add(taxonProfile:TaxonProfile) {

      var properties = scala.collection.mutable.Map[String,String]()
      properties.put("guid", taxonProfile.guid)
      properties.put("scientificName", taxonProfile.scientificName)
      properties.put("commonName", taxonProfile.commonName)
      properties.put("rankString", taxonProfile.rankString)
      if(taxonProfile.habitats!=null && taxonProfile.habitats.size>0){
        val habitatString = taxonProfile.habitats.reduceLeft(_+","+_)
        properties.put("habitats", habitatString)
      }
      properties.put("left", taxonProfile.left)
      properties.put("right", taxonProfile.right)
      if(taxonProfile.sensitive != null && taxonProfile.sensitive.size >0){
        properties.put("sensitive", Json.toJSON(taxonProfile.sensitive.asInstanceOf[Array[AnyRef]]))
      }
      persistenceManager.put(taxonProfile.guid, columnFamily, properties.toMap)
  }
}

/**
 * A DAO for attribution data. The source of this data should be the collectory.
 *
 * There is probably only a couple of hundred
 */
object AttributionDAO {

  import ReflectBean._
  private val columnFamily = "attr"
  //can't use a scala hashap because missing keys return None not null...
  private val lru = new org.apache.commons.collections.map.LRUMap(10000)//new HashMap[String, Option[Attribution]]
  private val persistenceManager = Config.getInstance(classOf[PersistenceManager]).asInstanceOf[PersistenceManager]
//  private val lru = new ConcurrentLinkedHashMap.Builder[String, Option[Attribution]]()
//      .maximumWeightedCapacity(1000)
//      .build();
  private val lock : AnyRef = new Object()

  /**
   * Persist the attribution information.
   */
  def add(institutionCode:String, collectionCode:String, attribution:Attribution){
    val guid = institutionCode.toUpperCase +"|"+collectionCode.toUpperCase
    val map = FullRecordMapper.mapObjectToProperties(attribution)
    persistenceManager.put(guid,"attr",map)
  }

 

  /**
   * Retrieve attribution via institution/collection codes.
   * We need to ensure that the cache is large enough to hold all possible values
   * <ol>
   * <li> Check if it is in the local cache</li>
   * <li> Request update from collectory service (cache it locally and in cassandra)</li>
   * <li> If service can not be contacted get the value from cassandra (cache it) </li>
   * </ol>
   */
  def getByCodes(institutionCode:String, collectionCode:String) : Option[Attribution] = {

    if(institutionCode!=null && collectionCode!=null){
      val uuid = institutionCode.toUpperCase+"|"+collectionCode.toUpperCase
      
      val cachedObject = lru.get(uuid)      
      if(cachedObject!=null){
        cachedObject.asInstanceOf[Option[Attribution]]
      } else {
          val map = persistenceManager.get(uuid,"attr")
          val result = {
              if(!map.isEmpty){
                val attribution = new Attribution
                FullRecordMapper.mapPropertiesToObject(attribution,map.get)
                Some(attribution)
              } else {
                None
              }
          }
          lock.synchronized { lru.put(uuid,result) }
          //lru.put(uuid,result)
          result
      }
    } else {
        None
    }
  }
}

/**
 * DAO for location lookups.
 */
object LocationDAO {

  private val columnFamily = "loc"
  private val lock : AnyRef = new Object()
  private val lru = new org.apache.commons.collections.map.LRUMap(10000)
  private val persistenceManager = Config.getInstance(classOf[PersistenceManager]).asInstanceOf[PersistenceManager]
//  private val lru = new ConcurrentLinkedHashMap.Builder[String, Option[Location]]()
//      .maximumWeightedCapacity(10000)
//      .build();
  /**
   * Add a tag to a location
   */
  def addTagToLocation (latitude:Float, longitude:Float, tagName:String, tagValue:String) {
    val guid = latitude +"|"+longitude
    persistenceManager.put(guid, columnFamily, "decimalLatitude",latitude.toString)
    persistenceManager.put(guid, columnFamily, "decimalLongitude",longitude.toString)
    persistenceManager.put(guid, columnFamily, tagName,tagValue)
  }

  /**
   * Add a region mapping for this point.
   */
  def addRegionToPoint (latitude:Double, longitude:Double, mapping:Map[String,String]) {
    val guid = latitude +"|"+longitude
    var properties = scala.collection.mutable.Map[String,String]()
    properties ++= mapping
    properties.put("decimalLatitude", latitude.toString)
    properties.put("decimalLongitude", longitude.toString)
    persistenceManager.put(guid, columnFamily, properties.toMap)
  }

  /**
   * Round coordinates to 4 decimal places.
   */
  protected def roundCoord(x:String) : String = {
    try {
      (((x.toFloat * 10000).toInt).toFloat / 10000).toString
    } catch {
      case e:NumberFormatException => x
    }
  }
  /**
   * Returns the persistence storage primary key for the supplied coordinates
   */
  def getLatLongKey(latitude:String, longitude:String) :Option[String]={
    if(latitude != null && longitude != null)
      return Some(roundCoord(latitude)+"|"+roundCoord(longitude))
    None
  }

  /**
   * Get location information for point.
   * For geo spatial requirements we don't want to round the latitude , longitudes 
   */
  def getByLatLon(latitude:String, longitude:String) : Option[Location] = {
    val uuid =  latitude+"|"+longitude //roundCoord(latitude)+"|"+roundCoord(longitude)

    val cachedObject = lock.synchronized { lru.get(uuid) }
    //val cachedObject = lru.get(uuid)

    if(cachedObject!=null){
        cachedObject.asInstanceOf[Option[Location]]
    } else {
        val map = persistenceManager.get(uuid,"loc")
        if(!map.isEmpty){
          val location = new Location
          FullRecordMapper.mapPropertiesToObject(location,map.get)
          FullRecordMapper.mapPropertiesToObject(location.environmentalLayers, map.get)
          FullRecordMapper.mapPropertiesToObject(location.contextualLayers, map.get)
          //lock.synchronized { lru.put(uuid,Some(location)) }
          lock.synchronized {lru.put(uuid,Some(location))}
          Some(location)
        } else {
          //lock.synchronized { lru.put(uuid,None) }
          lock.synchronized {lru.put(uuid,None)}
          None
        }
    }
  }
}