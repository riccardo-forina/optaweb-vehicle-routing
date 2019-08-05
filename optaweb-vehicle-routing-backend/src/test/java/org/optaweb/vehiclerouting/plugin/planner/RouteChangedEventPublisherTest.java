/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaweb.vehiclerouting.plugin.planner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.optaplanner.examples.vehiclerouting.domain.Customer;
import org.optaplanner.examples.vehiclerouting.domain.Depot;
import org.optaplanner.examples.vehiclerouting.domain.Vehicle;
import org.optaplanner.examples.vehiclerouting.domain.VehicleRoutingSolution;
import org.optaplanner.examples.vehiclerouting.domain.location.RoadLocation;
import org.optaweb.vehiclerouting.service.route.RouteChangedEvent;
import org.optaweb.vehiclerouting.service.route.ShallowRoute;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@ExtendWith(MockitoExtension.class)
class RouteChangedEventPublisherTest {

    @Mock
    private ApplicationEventPublisher publisher;
    @InjectMocks
    private RouteChangedEventPublisher routeChangedEventPublisher;

    @Test
    void should_covert_solution_to_event_and_publish_it() {
        routeChangedEventPublisher.publishRoute(SolutionUtil.emptySolution());
        Mockito.verify(publisher).publishEvent(Mockito.any(RouteChangedEvent.class));
    }

    @Test
    void empty_solution_should_have_zero_routes_vehicles_etc() {
        VehicleRoutingSolution solution = SolutionUtil.emptySolution();

        RouteChangedEvent event = RouteChangedEventPublisher.solutionToEvent(solution, this);

        assertThat(event.vehicleIds()).isEmpty();
        assertThat(event.depotId()).isEmpty();
        assertThat(event.routes()).isEmpty();
        assertThat(event.distance()).isEqualTo("0h 0m 0s");
    }

    @Test
    void solution_with_vehicles_and_no_depot_should_have_zero_routes() {
        VehicleRoutingSolution solution = SolutionUtil.emptySolution();
        long vehicleId = 1;
        SolutionUtil.addVehicle(solution, vehicleId);

        RouteChangedEvent event = RouteChangedEventPublisher.solutionToEvent(solution, this);

        assertThat(event.vehicleIds()).containsExactly(vehicleId);
        assertThat(event.depotId()).isEmpty();
        assertThat(event.routes()).isEmpty();
        assertThat(event.distance()).isEqualTo("0h 0m 0s");
    }

    @Test
    void nonempty_solution_without_vehicles_should_have_zero_routes() {
        long depotId = 1;
        long visitId = 2;
        VehicleRoutingSolution solution = SolutionUtil.emptySolution();
        SolutionUtil.addDepot(solution, new RoadLocation(depotId, 1.0, 1.0));
        SolutionUtil.addCustomer(solution, new RoadLocation(visitId, 2.0, 2.0));

        RouteChangedEvent event = RouteChangedEventPublisher.solutionToEvent(solution, this);

        assertThat(event.vehicleIds()).isEmpty();
        assertThat(event.depotId()).contains(depotId);
        assertThat(event.routes()).isEmpty();
        assertThat(event.distance()).isEqualTo("0h 0m 0s");
    }

    @Test
    void initialized_solution_should_have_one_route_per_vehicle() {
        // arrange
        VehicleRoutingSolution solution = SolutionUtil.emptySolution();
        long vehicleId1 = 1001;
        long vehicleId2 = 2001;
        SolutionUtil.addVehicle(solution, vehicleId1);
        SolutionUtil.addVehicle(solution, vehicleId2);

        long depotId = 1;
        long visitId = 2;
        Depot depot = SolutionUtil.addDepot(solution, new RoadLocation(depotId, 1.0, 1.0));
        Customer customer = SolutionUtil.addCustomer(solution, new RoadLocation(visitId, 2.0, 2.0));

        for (Vehicle vehicle : solution.getVehicleList()) {
            vehicle.setDepot(depot);
            vehicle.setNextCustomer(customer);
            customer.setPreviousStandstill(vehicle);
        }

        // act
        RouteChangedEvent event = RouteChangedEventPublisher.solutionToEvent(solution, this);

        // assert
        assertThat(event.routes()).hasSameSizeAs(solution.getVehicleList());
        assertThat(event.routes().stream().mapToLong(value -> value.vehicleId))
                .containsExactlyInAnyOrder(vehicleId1, vehicleId2);

        for (ShallowRoute route : event.routes()) {
            assertThat(route.depotId).isEqualTo(depot.getId());
            // visits shouldn't include the depot
            assertThat(route.visitIds).containsExactly(visitId);
        }

        assertThat(event.vehicleIds()).containsExactlyInAnyOrder(vehicleId1, vehicleId2);
        assertThat(event.depotId()).contains(depotId);
        assertThat(event.distance()).isEqualTo("0h 0m 0s");
    }

    @Test
    void fail_fast_if_vehicles_next_customer_doesnt_exist() {
        VehicleRoutingSolution solution = SolutionUtil.emptySolution();
        Depot depot = SolutionUtil.addDepot(solution, new RoadLocation(1, 1.0, 1.0));
        Vehicle vehicle = SolutionUtil.addVehicle(solution, 1);
        SolutionUtil.moveAllVehiclesTo(solution, depot);
        Customer customer = SolutionUtil.addCustomer(solution, new RoadLocation(2, 2.0, 2.0));
        vehicle.setNextCustomer(customer);
        solution.getCustomerList().clear();
        solution.getLocationList().clear();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RouteChangedEventPublisher.solutionToEvent(solution, this));
    }

    @Test
    void vehicle_without_a_depot_is_illegal() {
        VehicleRoutingSolution solution = SolutionUtil.emptySolution();
        SolutionUtil.addDepot(solution, new RoadLocation(1, 1.0, 1.0));
        SolutionUtil.addVehicle(solution, 1);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RouteChangedEventPublisher.solutionToEvent(solution, this));
    }
}
