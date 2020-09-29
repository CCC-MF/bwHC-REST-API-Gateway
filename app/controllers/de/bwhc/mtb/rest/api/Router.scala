package de.bwhc.mtb.rest.api



import javax.inject.Inject

import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._


//import de.bwhc.rest.auth._


class Router @Inject()(
  examples: ExampleProvider,
  dataEntry: DataEntryController,
  queryController: QueryController,
//  userAction: UserAction
)
extends SimpleRouter
{

  override def routes: Routes = {

    //-------------------------------------------------------------------------
    // Data example endpoints
    //-------------------------------------------------------------------------
    case GET(p"/data/examples/MTBFile")         => examples.mtbfile


    //-------------------------------------------------------------------------
    // Data Management endpoints                                               
    //-------------------------------------------------------------------------

    case POST(p"/data/MTBFile")                 => dataEntry.processUpload

    case GET(p"/data/Patient")                  => dataEntry.patients
//    case GET(p"/data/Patient")                  => userAction.andThen( dataEntry.patients )



    case GET(p"/data/MTBFile/$id")              => dataEntry.mtbfile(id)
    case GET(p"/data/DataQualityReport/$id")    => dataEntry.dataQualityReport(id)

    case DELETE(p"/data/Patient/$id")           => dataEntry.delete(id)


    //-------------------------------------------------------------------------
    // ZPM QC Reports                                                          
    //-------------------------------------------------------------------------
    case GET(p"/reporting/LocalQCReport")       => queryController.getLocalQCReport
    case GET(p"/reporting/GlobalQCReport")      => queryController.getGlobalQCReport


    //-------------------------------------------------------------------------
    // MTBFile Cohort Queries                                                  
    //-------------------------------------------------------------------------
 
    case POST(p"/query")                        => queryController.submit
    case POST(p"/query/$id")                    => queryController.update(id)
    case PUT(p"/query/$id/filter")              => queryController.applyFilter(id)

    case GET(p"/query/$id")                     => queryController.query(id)
    case GET(p"/query/$id/Patient")             => queryController.patientsFrom(id)
    case GET(p"/query/$id/MTBFile/$patId")      => queryController.mtbfileFrom(id,patId)


    case POST(p"/peer2peer/query")              => queryController.processPeerToPeerQuery
    case GET(p"/peer2peer/LocalQCReport")       => queryController.getLocalQCReport

  }


}
