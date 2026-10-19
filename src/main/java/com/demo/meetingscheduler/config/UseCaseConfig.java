package com.demo.meetingscheduler.config;

import com.demo.meetingscheduler.application.port.in.ManageMeetingsUseCase;
import com.demo.meetingscheduler.application.port.in.ManageTimeSlotsUseCase;
import com.demo.meetingscheduler.application.port.in.ManageUsersUseCase;
import com.demo.meetingscheduler.application.port.in.QueryAvailabilityUseCase;
import com.demo.meetingscheduler.application.port.outbound.MeetingRepository;
import com.demo.meetingscheduler.application.port.outbound.TimeSlotRepository;
import com.demo.meetingscheduler.application.port.outbound.UserRepository;
import com.demo.meetingscheduler.application.service.AvailabilityService;
import com.demo.meetingscheduler.application.service.MeetingService;
import com.demo.meetingscheduler.application.service.TimeSlotService;
import com.demo.meetingscheduler.application.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root: the application services carry no Spring stereotype, they are wired to the outbound
 * ports (implemented by the adapters) here.
 */
@Configuration(proxyBeanMethods = false)
class UseCaseConfig {

	@Bean
	ManageUsersUseCase manageUsersUseCase(UserRepository users) {
		return new UserService(users);
	}

	@Bean
	ManageTimeSlotsUseCase manageTimeSlotsUseCase(TimeSlotRepository slots, MeetingRepository meetings,
			UserRepository users) {
		return new TimeSlotService(slots, meetings, users);
	}

	@Bean
	ManageMeetingsUseCase manageMeetingsUseCase(MeetingRepository meetings, TimeSlotRepository slots,
			UserRepository users) {
		return new MeetingService(meetings, slots, users);
	}

	@Bean
	QueryAvailabilityUseCase queryAvailabilityUseCase(TimeSlotRepository slots, UserRepository users) {
		return new AvailabilityService(slots, users);
	}

}
