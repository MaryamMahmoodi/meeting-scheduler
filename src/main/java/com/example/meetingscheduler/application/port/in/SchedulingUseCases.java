package com.example.meetingscheduler.application.port.in;
import com.example.meetingscheduler.domain.AvailabilityStatus;
import java.time.OffsetDateTime;
import java.util.*;
public interface SchedulingUseCases {
  UUID createUser(String name,String email); Map<String,Object> getUser(UUID id); List<Map<String,Object>> users();
  UUID createSlot(UUID userId,OffsetDateTime start,OffsetDateTime end);
  void updateSlot(UUID id,OffsetDateTime start,OffsetDateTime end); void deleteSlot(UUID id);
  void setSlotStatus(UUID id,AvailabilityStatus status);
  List<Map<String,Object>> availability(UUID userId,OffsetDateTime from,OffsetDateTime to);
  UUID book(UUID organizerId,String title,String description,OffsetDateTime start,OffsetDateTime end,List<UUID> participants);
  Map<String,Object> meeting(UUID id); void cancel(UUID id);
}
