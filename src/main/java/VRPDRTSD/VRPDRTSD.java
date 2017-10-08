package VRPDRTSD;

import ProblemRepresentation.*;
import Algorithms.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * @author renansantos
 */
public class VRPDRTSD implements Algorithm {

    private ProblemData data;
    private String instanceName;
    private String nodesInstanceName;
    private String adjacenciesInstanceName;
    private int numberOfVehicles;
    private int vehicleCapacity;
    private double maxDistance;
    private double minDistance;
    private int minTimeWindowLower;
    private int maxTimeWindowLower;
    private int minTimeWindowUpper;
    private int maxTimeWindowUpper;
    private Map<Node, List<Request>> requestsThatBoardsInNode;
    private Map<Node, List<Request>> requestsThatLeavesInNode;
    private int maxLoadIndex;
    private int minLoadIndex;
    private Solution solution;
    private List<Request> candidates = new ArrayList<>();
    private List<Request> feasibleRequests = new ArrayList<>();
    private Request candidate;
    private Route currentRoute;

    public VRPDRTSD(String instanceName, String nodesInstanceName, String adjacenciesInstanceName,
            int numberOfVehicles, int vehicleCapacity) {
        this.instanceName = instanceName;
        this.nodesInstanceName = nodesInstanceName;
        this.adjacenciesInstanceName = adjacenciesInstanceName;
        this.numberOfVehicles = numberOfVehicles;
        this.vehicleCapacity = vehicleCapacity;
        this.readInstance();

    }

    public ProblemData getData() {
        return data;
    }

    public void setData(ProblemData data) {
        this.data = data;
    }

    public Solution getSolution() {
        return solution;
    }

    public void originalRequestsFeasibilityAnalysis() {
        for (Request request : data.getRequests()) {
            request.determineInicialFeasibility(data.getCurrentTime(), data.getCurrentNode(), data.getDuration());
        }
    }

    public void requestsFeasibilityAnalysis() {
        data.setCurrentNode(data.getNodes().get(0));
        for (Request request : candidates) {
            request.determineInicialFeasibility(data.getCurrentTime(), data.getCurrentNode(), data.getDuration());
        }
    }

    public void requestsFeasibilityAnalysisInConstructionFase() {
        for (Request request : candidates) {
            request.determineFeasibilityInConstructionFase(data.getCurrentTime(), data.getLastPassengerAddedToRoute(),
                    data.getCurrentNode(), data.getDuration());
//            request.determineFeasibilityInConstructionFase(data.getCurrentTime(), data.getCurrentNode(), data.getDuration());
        }
    }

    public void setDistanceToAttendEveryRequest() {
        data.getRequests().forEach(r -> r.setDistanceToAttendThisRequest(data.getCurrentNode(), data.getDistance()));
    }

    public void findMaxAndMinDistance() {
        maxDistance = data.getRequests().stream()
                .mapToDouble(Request::getDistanceToAttendThisRequest)
                .max().getAsDouble();
        minDistance = data.getRequests().stream()
                .mapToDouble(Request::getDistanceToAttendThisRequest)
                .min().getAsDouble();
    }

    public void findMaxAndMinTimeWindowLower() {
        minTimeWindowLower = data.getRequests().stream()
                .mapToInt(Request::getDeliveryTimeWindowLowerInMinutes)
                .min().getAsInt();
        maxTimeWindowLower = data.getRequests().stream()
                .mapToInt(Request::getDeliveryTimeWindowLowerInMinutes)
                .max().getAsInt();
    }

    public void findMaxAndMinTimeWindowUpper() {
        minTimeWindowUpper = data.getRequests().stream()
                .mapToInt(Request::getDeliveryTimeWindowUpperInMinutes)
                .min().getAsInt();
        maxTimeWindowUpper = data.getRequests().stream()
                .mapToInt(Request::getDeliveryTimeWindowUpperInMinutes)
                .max().getAsInt();
    }

    public void separateRequestsWhichBoardsAndLeavesInNodes() {
        requestsThatBoardsInNode = data.getRequests().stream()
                .collect(Collectors.groupingBy(Request::getOrigin));
        requestsThatLeavesInNode = data.getRequests().stream()
                .collect(Collectors.groupingBy(Request::getDestination));
    }

    public void setLoadIndexForEveryNode() {
        data.getNodes().forEach(n -> n.setLoadIndex(requestsThatBoardsInNode, requestsThatLeavesInNode));
    }

    public void findMaxAndMinLoadIndex() {
        maxLoadIndex = data.getNodes().stream()
                .mapToInt(Node::getLoadIndex)
                .max().getAsInt();
        minLoadIndex = data.getNodes().stream()
                .mapToInt(Node::getLoadIndex)
                .min().getAsInt();
    }

    public ArrayList<Request> getListOfFeasibleRequests() {
        return data.getRequests().stream().filter(Request::isFeasible).collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public void readInstance() {
        data = new ProblemData(instanceName, nodesInstanceName, adjacenciesInstanceName, numberOfVehicles, vehicleCapacity);
    }

    @Override
    public void buildGreedySolution() {
        initializeSolution();
        initializeCandidatesSet();
        while (stoppingCriterionIsFalse()) {
            startNewRoute();
            requestsFeasibilityAnalysis();
            while (hasFeasibleRequests() && hasEmptySeatInVehicle()) {
                findBestCandidateUsingRRF();
                addCandidateIntoRoute();
                actualizeRequestsData();
                if (hasEmptySeatInVehicle()) {
                    findOtherRequestsThatCanBeAttended();
                }
                requestsFeasibilityAnalysisInConstructionFase();
            }
            finalizeRoute();
            addRouteInSolution();
            System.out.println(currentRoute);
        }
        //finalizeSolution();
    }

    public void initializeSolution() {
        solution = new Solution();
    }

    public void initializeCandidatesSet() {
        originalRequestsFeasibilityAnalysis();
        prepareAndSetRequestsData();
        setRequestFeasibilityParameters();
        initializeCandidates();
    }

    public void prepareAndSetRequestsData() {
        setDistanceToAttendEveryRequest();
        findMaxAndMinDistance();
        findMaxAndMinTimeWindowLower();
        findMaxAndMinTimeWindowUpper();
        separateRequestsWhichBoardsAndLeavesInNodes();
        setLoadIndexForEveryNode();
        findMaxAndMinLoadIndex();
    }

    public void setRequestFeasibilityParameters() {
        for (Request request : data.getRequests()) {
            request.setDistanceRankingFunction(maxDistance, minDistance);
            request.setDeliveryTimeWindowLowerRankingFunction(maxTimeWindowLower, minTimeWindowLower);
            request.setDeliveryTimeWindowUpperRankingFunction(maxTimeWindowUpper, minTimeWindowUpper);
            request.setOriginNodeRankingFunction(maxLoadIndex, minLoadIndex);
            request.setDestinationNodeRankingFunction(maxLoadIndex, minLoadIndex);
            request.setRequestRankingFunction(0.1, 0.5, 0.1, 0.1, 0.1);
        }
    }

    public void initializeCandidates() {
        data.getRequests().sort(Comparator.comparing(Request::getRequestRankingFunction).reversed());
        candidates.addAll(data.getRequests());
    }

    public void findBestCandidateUsingRRF() {
        candidates.sort(Comparator.comparing(Request::getRequestRankingFunction).reversed());
        List<Request> feasibleCandidates = new ArrayList<>();
        feasibleCandidates.addAll(candidates.stream().filter(Request::isFeasible).collect(Collectors.toCollection(ArrayList::new)));
        if (feasibleCandidates.size() != 0) {
            candidate = feasibleCandidates.get(0);
        }
    }

    public void addCandidateIntoRoute() {
        if (currentRoute.getIntegerRouteRepresetation().size() == 0) {
            data.setCurrentTime(candidate.getDeliveryTimeWindowLower());
        } else {
            Duration displacementTime = data.getDuration()[data.getLastPassengerAddedToRoute().getDestination().getId()][candidate.getDestination().getId()];
            data.setCurrentTime(data.getCurrentTime().plus(displacementTime));
        }

        int indexOfCandidate = candidates.indexOf(candidate);
        data.setLastPassengerAddedToRoute(candidates.get(indexOfCandidate));
        data.setCurrentNode(data.getLastPassengerAddedToRoute().getDestination());

        candidate.setDeliveryTime(data.getCurrentTime());
        currentRoute.addValueInIntegerRepresentation(candidate.getId());

        data.getCurrentVehicle().boardPassenger();
        scheduleDeliveryTimeInRouteRepresentation();
    }

    public void actualizeRequestsData() {
        candidates.remove(candidate);
        requestsThatLeavesInNode.get(data.getCurrentNode()).remove(candidate);
    }

    public boolean stoppingCriterionIsFalse() {
        return !candidates.isEmpty() && !data.getAvaibleVehicles().isEmpty();
    }

    public void startNewRoute() {
        currentRoute = new Route();
        data.setCurrentVehicle(new Vehicle(data.getAvaibleVehicles().get(0)));
        data.getAvaibleVehicles().remove(0);
        data.setCurrentNode(data.getNodes().get(0));
        data.setCurrentTime(LocalDateTime.of(2017, 1, 1, 0, 0, 0));
    }

    public boolean hasFeasibleRequests() {
        return candidates.stream()
                .filter(r -> r.isFeasible())
                .collect(Collectors.toCollection(ArrayList::new)).size() != 0;
    }

    public boolean hasEmptySeatInVehicle() {
        return data.getCurrentVehicle().getBusySeats() < data.getCurrentVehicle().getCapacity();
    }

    public void scheduleDeliveryTimeInRouteRepresentation() {
        currentRoute.addValueInIntegerRepresentation(-1 * candidate.getDeliveryTimeInMinutes());
    }

    public void scheduleDeliveryTime(Request request) {
        currentRoute.addValueInIntegerRepresentation(-1 * request.getDeliveryTimeWindowLowerInMinutes());
    }

    public void scheduleDeliveryTime(LocalDateTime ldt) {
        currentRoute.addValueInIntegerRepresentation(-1 * (ldt.getHour() * 60 + ldt.getMinute()));
    }

    private Integer getFirstDeliveryTime() {
        return currentRoute.getIntegerRouteRepresetation().get(1);
    }

    private Integer getFirstDeliveryPassengerId() {
        return currentRoute.getIntegerRouteRepresetation().get(0);
    }

    public void addRouteInSolution() {
        solution.addRoute(currentRoute);
    }

    public void finalizeRoute() {
        //schedulePickUpTime();
        scheduleRoute();
        //addDepotInRoute();
        buildSequenceOfAttendedRequests();
        buildNodesSequence();
        calculateDistanceTraveled();
        calculateTravelTime();
        calculateTotalViolationOfTheDeliveryTimeWindow();
    }

    public void scheduleRoute() {
        List<Integer> deliveryIdSequence = getOnlyIdSequence();
        List<Integer> pickupIdSequence = getOnlyIdSequence();

        List<Integer> idSequence = new ArrayList<>();
        idSequence.addAll(pickupIdSequence);
        idSequence.addAll(deliveryIdSequence);

        int positionInSequenceOfFirstDelivery = 0;

        Set<Integer> visitedIds = new HashSet<>();

        for (int i = 0; i < idSequence.size(); i++) {
            int id = idSequence.get(i);
            if (visitedIds.contains(id)) {
                positionInSequenceOfFirstDelivery = i;
                break;
            }
            visitedIds.add(id);
        }

        List<Integer> deliveryTimes = new ArrayList<>();
        List<Integer> pickupTimes = new ArrayList<>();
        int currentTimeForDelivery = getRequestUsingId(idSequence.get(positionInSequenceOfFirstDelivery))
                .getDeliveryTimeWindowLowerInMinutes();
        int currentTimeForPickup = -currentTimeForDelivery;
        deliveryTimes.add(-currentTimeForDelivery);

        for (int i = positionInSequenceOfFirstDelivery; i < idSequence.size() - 1; i++) {
            int originPassengerId = idSequence.get(i);
            int destinationPassengerId = idSequence.get(i + 1);
            Request originRequest = getRequestUsingId(originPassengerId);
            Request destinationRequest = getRequestUsingId(destinationPassengerId);
            int timeBetween;

            if (visitedIds.contains(originPassengerId)) {
                if (visitedIds.contains(destinationPassengerId)) {
                    timeBetween = (int) data.getDuration()[originRequest.getDestination().getId()][destinationRequest.getDestination().getId()]
                            .getSeconds() / 60;
                } else {
                    timeBetween = (int) data.getDuration()[originRequest.getDestination().getId()][destinationRequest.getOrigin().getId()]
                            .getSeconds() / 60;
                }
                deliveryTimes.add(-currentTimeForDelivery - timeBetween);
                currentTimeForDelivery -= -timeBetween;
            } else {
                if (visitedIds.contains(destinationPassengerId)) {
                    timeBetween = (int) data.getDuration()[originRequest.getOrigin().getId()][destinationRequest.getDestination().getId()]
                            .getSeconds() / 60;
                } else {
                    timeBetween = (int) data.getDuration()[originRequest.getOrigin().getId()][destinationRequest.getOrigin().getId()]
                            .getSeconds() / 60;
                }
                deliveryTimes.add(-currentTimeForDelivery - timeBetween);
                currentTimeForDelivery -= -timeBetween;
            }
        }

        deliveryIdSequence.add(0);
        pickupIdSequence.add(0, 0);

        int timeBetween = (int) data.getDuration()[getRequestUsingId(idSequence.get(idSequence.size() - 1)).getDestination().getId()][0]
                .getSeconds() / 60;
        deliveryTimes.add(-currentTimeForDelivery - timeBetween);
        //deliveryTimes.add(-currentTimeForDelivery - 10);

        List<Long> displacementTimesBetweenPassengers = new ArrayList<>();
        for (int i = 0; i < pickupIdSequence.size() - 1; i++) {
            int originPassengerId = pickupIdSequence.get(i);
            int destinationPassengerId = pickupIdSequence.get(i + 1);
            long displacementTime = 0;

            Request originRequest = getRequestUsingId(originPassengerId);
            Request destinationRequest = getRequestUsingId(destinationPassengerId);

            if (originRequest == null) {
                displacementTime = data.getDuration()[0][destinationRequest.getOrigin().getId()]
                        .getSeconds();
            } else if (destinationRequest == null) {
                displacementTime = data.getDuration()[originRequest.getOrigin().getId()][0]
                        .getSeconds();
            } else {
                displacementTime = data.getDuration()[originRequest.getOrigin().getId()][destinationRequest.getOrigin().getId()]
                        .getSeconds();
            }
            displacementTimesBetweenPassengers.add(-displacementTime / 60);
        }
        Request lastPickup = getRequestUsingId(idSequence.get(positionInSequenceOfFirstDelivery - 1));
        Request firstDelivery = getRequestUsingId(idSequence.get(positionInSequenceOfFirstDelivery));
        long timeBetweenLastPickupAndFirstDelivery = data
                .getDuration()[lastPickup.getOrigin().getId()][firstDelivery.getDestination().getId()].getSeconds() / 60;

        displacementTimesBetweenPassengers.add(-timeBetweenLastPickupAndFirstDelivery);
        List<Integer> times = new ArrayList<>();
        for (int i = displacementTimesBetweenPassengers.size() - 1; i >= 0; i--) {
            currentTimeForPickup -= displacementTimesBetweenPassengers.get(i);
            times.add((int) currentTimeForPickup);
        }

        Collections.reverse(times);
        times.addAll(deliveryTimes);

        //fazer um merge dos ids com os tempos
        idSequence.add(0);
        idSequence.add(0, 0);
        List<Integer> integerRepresentation = new ArrayList<>();
        for (int i = 0; i < idSequence.size(); i++) {
            integerRepresentation.add(idSequence.get(i));
            integerRepresentation.add(times.get(i));
        }

        currentRoute.clearIntegerRepresentation();
        currentRoute.setIntegerRouteRepresetation(integerRepresentation);
        currentRoute.setPickupAndDeliveryTimeForEachAttendedRequest(data);
    }

    private List<Integer> getOnlyIdSequence() {
        List<Integer> idSequence = currentRoute.getIntegerRouteRepresetation()
                .stream().filter(u -> u.intValue() > 0)
                .collect(Collectors.toCollection(ArrayList::new));
        return idSequence;
    }

    public Request getRequestUsingId(Integer id) {
        if (id != 0) {
            return data.getRequests().stream().filter(u -> u.getId().equals(id)).findAny().get();
        } else {
            return null;
        }
    }

    public void schedulePickUpTime() {
        List<Integer> pickupSequence = getOnlyIdSequence();

        List<Integer> pickupTimes = new ArrayList<>();
        List<Integer> timesBetween = new ArrayList<>();
        int startDeliveryTimeInteger = getFirstDeliveryTime();
        int startDeliveryPassenger = getFirstDeliveryPassengerId();
        int currentTime = startDeliveryTimeInteger;
        pickupSequence.add(startDeliveryPassenger);
        Request passengerOrigin = null, passengerDestination = null;

        for (int i = 0; i < pickupSequence.size() - 1; i++) {

            for (Request request : data.getRequests()) {
                if (request.getId().equals(pickupSequence.get(i))) {
                    passengerOrigin = request;
                }
                if (request.getId().equals(pickupSequence.get(i + 1))) {
                    passengerDestination = request;
                }
            }

            Duration timeBetween = null;
            if (passengerOrigin.getId() == passengerDestination.getId()) {
                timeBetween = data.getDuration()[passengerOrigin.getOrigin().getId()][passengerDestination.getDestination().getId()];
            } else {
                timeBetween = data.getDuration()[passengerOrigin.getOrigin().getId()][passengerDestination.getOrigin().getId()];
            }

            int integerTimeBetween = (int) timeBetween.toHours() * 60 + (int) timeBetween.toMinutes();
            timesBetween.add(integerTimeBetween);
        }

        for (int i = timesBetween.size() - 1; i >= 0; i--) {
            currentTime = currentTime + timesBetween.get(i);
            pickupTimes.add(currentTime);
        }

        Collections.reverse(pickupTimes);

        List<Integer> pickupScheduledSequence = new ArrayList<>();

        for (int i = 0; i < pickupSequence.size() - 1; i++) {
            pickupScheduledSequence.add(pickupSequence.get(i));
            pickupScheduledSequence.add(pickupTimes.get(i));
        }
        pickupScheduledSequence.addAll(currentRoute.getIntegerRouteRepresetation());
        currentRoute.getIntegerRouteRepresetation().clear();
        currentRoute.getIntegerRouteRepresetation().addAll(pickupScheduledSequence);

        for (int i = 0; i < pickupScheduledSequence.size() / 2 - 1; i = i + 2) {
            for (Request request : data.getRequests()) {
                if (request.getId().equals(pickupScheduledSequence.get(i))) {
                    request.setPickUpTime(pickupScheduledSequence.get(i + 1));
                }
            }
        }
    }

    private void addDepotInRoute() {
        this.currentRoute.getIntegerRouteRepresetation().add(0);
        this.currentRoute.getIntegerRouteRepresetation().add(0, 0);
    }

    private void buildSequenceOfAttendedRequests() {
        currentRoute.buildSequenceOfAttendedRequests(data);
    }

    private void buildNodesSequence() {
        currentRoute.buildNodesSequence(data);
    }

    private void calculateTotalViolationOfTheDeliveryTimeWindow() {
        currentRoute.calculateTotalViolationOfTheDeliveryTimeWindow();
    }

    private void calculateTravelTime() {
        currentRoute.calculateTravelTime(data);
    }

    private void calculateDistanceTraveled() {
        currentRoute.calculateDistanceTraveled(data);
    }

    public void findOtherRequestsThatCanBeAttended() {
        List<Request> otherRequestsToAdd = new ArrayList<>();
        for (Request request : requestsThatLeavesInNode.get(data.getCurrentNode())) {
            if (currentTimeIsWithInDeliveryTimeWindow(request)) {
                otherRequestsToAdd.add(request);
            }
        }

        if (otherRequestsToAdd.size() != 0) {
            otherRequestsToAdd.sort(Comparator.comparing(Request::getDeliveryTimeWindowLower));
        }

        while (otherRequestsToAdd.size() != 0) {
            candidate = otherRequestsToAdd.get(0);
            candidate.setDeliveryTime(data.getCurrentTime());
            currentRoute.addValueInIntegerRepresentation(otherRequestsToAdd.get(0).getId());
            data.setLastPassengerAddedToRoute(otherRequestsToAdd.get(0));
            data.getCurrentVehicle().boardPassenger();
            candidates.remove(otherRequestsToAdd.get(0));
            requestsThatLeavesInNode.get(data.getCurrentNode()).remove(otherRequestsToAdd.get(0));
            scheduleDeliveryTime(data.getCurrentTime());
            otherRequestsToAdd.remove(0);
        }

    }

    private boolean currentTimeIsWithInDeliveryTimeWindow(Request request) {
        return (data.getCurrentTime().isAfter(request.getDeliveryTimeWindowLower())
                || data.getCurrentTime().isEqual(request.getDeliveryTimeWindowLower()))
                && (data.getCurrentTime().isBefore(request.getDeliveryTimeWindowUpper())
                || data.getCurrentTime().isEqual(request.getDeliveryTimeWindowUpper()));
    }

}
