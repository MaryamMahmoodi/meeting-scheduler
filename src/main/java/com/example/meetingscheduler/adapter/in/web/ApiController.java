package com.example.meetingscheduler.adapter.in.web;
import com.example.meetingscheduler.application.port.in.SchedulingUseCases;
import com.example.meetingscheduler.domain.AvailabilityStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;
import java.util.*;
@RestController @RequestMapping("/api")
public class ApiController {
  private final SchedulingUseCases service;
  public ApiController(SchedulingUseCases service){this.service=service;}
  public record UserRequest(@NotBlank String name,@NotBlank @Email String email){}
  public record SlotRequest(@NotNull OffsetDateTime startsAt,@NotNull OffsetDateTime endsAt){}
  public record StatusRequest(@NotNull AvailabilityStatus status){}
  public record MeetingRequest(@NotNull UUID organizerId,@NotBlank @Size(max=200) String title,String description,
    @NotNull OffsetDateTime startsAt,@NotNull OffsetDateTime endsAt,List<UUID> participantIds){}
  @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> createUser(@Valid @RequestBody UserRequest r){UUID id=service.createUser(r.name(),r.email());return Map.of("id",id,"name",r.name(),"email",r.email());}
  @GetMapping("/users") public List<Map<String,Object>> users(){return service.users();}
  @GetMapping("/users/{id}") public Map<String,Object> user(@PathVariable UUID id){return service.getUser(id);}
  @PostMapping("/users/{id}/slots") @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> createSlot(@PathVariable UUID id,@Valid @RequestBody SlotRequest r){UUID slot=service.createSlot(id,r.startsAt(),r.endsAt());return Map.of("id",slot,"userId",id,"startsAt",r.startsAt(),"endsAt",r.endsAt(),"status","FREE");}
  @PutMapping("/slots/{id}") public Map<String,String> updateSlot(@PathVariable UUID id,@Valid @RequestBody SlotRequest r){service.updateSlot(id,r.startsAt(),r.endsAt());return Map.of("result","updated");}
  @DeleteMapping("/slots/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteSlot(@PathVariable UUID id){service.deleteSlot(id);}
  @PatchMapping("/slots/{id}/availability") public Map<String,String> status(@PathVariable UUID id,@Valid @RequestBody StatusRequest r){service.setSlotStatus(id,r.status());return Map.of("result","updated","status",r.status().name());}
  @GetMapping("/users/{id}/availability") public List<Map<String,Object>> availability(@PathVariable UUID id,@RequestParam OffsetDateTime from,@RequestParam OffsetDateTime to){return service.availability(id,from,to);}
  @GetMapping("/users/{id}/calendar") public List<Map<String,Object>> calendar(@PathVariable UUID id,@RequestParam OffsetDateTime from,@RequestParam OffsetDateTime to){return service.availability(id,from,to);}
  @PostMapping("/meetings") @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> book(@Valid @RequestBody MeetingRequest r){UUID id=service.book(r.organizerId(),r.title(),r.description(),r.startsAt(),r.endsAt(),r.participantIds());return service.meeting(id);}
  @GetMapping("/meetings/{id}") public Map<String,Object> meeting(@PathVariable UUID id){return service.meeting(id);}
  @DeleteMapping("/meetings/{id}") public Map<String,String> cancel(@PathVariable UUID id){service.cancel(id);return Map.of("result","cancelled");}
}
