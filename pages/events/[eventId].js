import { useRouter } from "next/router";
import { getEventById } from "../../dummy-data";
import { Fragment } from "react";
import EventSummary from "../../components/event-detail/event-summary";
import EventLogistics from "../../components/event-detail/event-logistics";
import EventContent from "../../components/event-detail/event-content";
import ErrorAlert from "../../components/ui/error-alert";

function EventDetailPage(params) {
  const router = useRouter();
  const eventId = router.query.eventId;
  const event = getEventById(eventId);

  console.log(event);

  if (!event) {
    return (
      <Fragment>
        <h1> THis is a huge header</h1>
        <ErrorAlert>
          <p>No Events found! </p>
          <div className="center">
            <Button link="/events">Show All Events</Button>
          </div>
        </ErrorAlert>        
      </Fragment>
    );
  }

  return (
    <Fragment>
      <EventSummary />
      <EventLogistics
        date={event.date}
        address={event.location}
        image={event.image}
        imageAlt={event.title}
      />
      <EventContent>
        <p>{event.description}</p>
      </EventContent>
    </Fragment>
  );
}

export default EventDetailPage;
