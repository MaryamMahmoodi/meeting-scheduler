package com.example.meetingscheduler.application.service;
import com.example.meetingscheduler.application.port.in.SchedulingUseCases;
import com.example.meetingscheduler.application.port.out.SchedulingRepository;
import com.example.meetingscheduler.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
@Service
public class SchedulingService implements SchedulingUseCases {
  private final SchedulingRepository repo;
  public SchedulingService(SchedulingRepository repo) { this.repo=repo; }
  @Transactional public UUID createUser(String n,String e) {
    if(n==null||n.isBlank()||e==null||e.isBlank()) throw new IllegalArgumentException("Name and email are required");
    return repo.createUser(n.trim(),e.trim().toLowerCase());
  }
  public Map<String,Object> getUser(UUID id){return repo.getUser(id);}
  public List<Map<String,Object>> users(){return repo.users();}
  @Transactional public UUID createSlot(UUID u,OffsetDateTime s,OffsetDateTime e){new TimeRange(s,e);return repo.createSlot(u,s,e);}
  @Transactional public void updateSlot(UUID id,OffsetDateTime s,OffsetDateTime e){new TimeRange(s,e);repo.updateSlot(id,s,e);}
  @Transactional public void deleteSlot(UUID id){repo.deleteSlot(id);}
  @Transactional public void setSlotStatus(UUID id,AvailabilityStatus s){repo.setSlotStatus(id,s);}
  public List<Map<String,Object>> availability(UUID u,OffsetDateTime f,OffsetDateTime t){new TimeRange(f,t);return repo.availability(u,f,t);}
  @Transactional public UUID book(UUID u,String title,String desc,OffsetDateTime s,OffsetDateTime e,List<UUID> p){
    new TimeRange(s,e); if(title==null||title.isBlank())throw new IllegalArgumentException("Meeting title is required");
    return repo.book(u,title.trim(),desc,s,e,p==null?List.of():p);
  }
  public Map<String,Object> meeting(UUID id){return repo.meeting(id);}
  @Transactional public void cancel(UUID id){repo.cancel(id);}
}
