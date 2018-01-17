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
public class VRPDRTSD implements Metaheuristic {

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
    //System.out.println(getRequestUsingId(230).isFeasible());
    }

    public void requestsFeasibilityAnalysisInConstructionFase() {
//        for (Request request : candidates) {
//            request.determineInicialFeasibility(data.getCurrentTime(), data.getCurrentNode(), data.getDuration());
//        }
        for (Request request : candidates) {
            request.determineFeasibilityInConstructionFase(data.getCurrentTime(), data.getLastPassengerAddedToRoute(),
                    data.getCurrentNode(), data.getDuration());
        }
        System.out.println(getRequestUsingId(230).isFeasible());
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
        }
        finalizeSolution();
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

    public void recalculateRRF() {
        setDistanceToAttendEveryRequest();
        findMaxAndMinDistance();
        findMaxAndMinTimeWindowLower();
        findMaxAndMinTimeWindowUpper();
        findMaxAndMinLoadIndex();
        setRequestFeasibilityParameters();
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
            candidate.setDeliveryTime(data.getCurrentTime());
        } else {
            Duration displacementTime = data.getDuration()[data.getLastPassengerAddedToRoute().getDestination().getId()][candidate.getDestination().getId()];
            data.setCurrentTime(data.getCurrentTime().plus(displacementTime));
            candidate.setDeliveryTime(data.getCurrentTime());
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
        recalculateRRF();
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
        addPickupSequence();
        buildSequenceOfAttendedRequests();
        scheduleRoute();
        buildNodesSequence();
        evaluateRoute();
    }

    private void addPickupSequence() {
        List<Integer> deliverySequence = getOnlyIdSequence();
        List<Integer> pickupSequence = getOnlyIdSequence();
        List<Integer> idSequence = new ArrayList<>();
        //buildPickupSequence(pickupSequence,deliverySequence);
        idSequence.addAll(pickupSequence);
        idSequence.addAll(deliverySequence);

        idSequence.add(0);
        idSequence.add(0, 0);

        currentRoute.clearIntegerRepresentation();
        currentRoute.setIntegerRouteRepresetation(idSequence);
    }

    private void buildPickupSequence(List<Integer> pickupSequence, List<Integer> deliverySequence) {
        Request firstDeliveryRequest = getRequestUsingId(deliverySequence.get(0));
        List<Request> deliveryPassengers = new ArrayList<>();

        for (int i = 0; i < deliverySequence.size(); i++) {
            Request requestToAdd = getRequestUsingId(deliverySequence.get(i));
            deliveryPassengers.add(requestToAdd);
        }

        deliveryPassengers.sort(Comparator.comparing(Request::getDeliveryTimeWindowLowerInMinutes));
        pickupSequence.clear();
        pickupSequence.addAll(deliveryPassengers.stream().map(Request::getId).collect(Collectors.toCollection(ArrayList::new)));
    }

    public void scheduleRoute() {
        currentRoute.scheduleRouteUsingBestScheduling(data);
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

    private void buildSequenceOfAttendedRequests() {
        currentRoute.buildSequenceOfAttendedRequests(data);
    }

    private void buildNodesSequence() {
        currentRoute.buildNodesSequence(data);
    }

    private void evaluateRoute() {
        this.currentRoute.evaluateRoute(data);
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

        while (otherRequestsToAdd.size() != 0 && hasEmptySeatInVehicle()) {
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

    private void finalizeSolution() {
        solution.calculateEvaluationFunction();
        solution.buildIntegerRepresentation();
    }

    @Override
    public void buildRandomSolution() {
        initializeSolution();
        initializeRandomCandidatesSet();
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
        }
        finalizeSolution();
    }

    public void buildSelfishSolution() {
        VRPDRTSD problem = new VRPDRTSD(instanceName, nodesInstanceName, adjacenciesInstanceName, numberOfVehicles, 1);
        problem.buildGreedySolution();
        this.solution = problem.getSolution();
    }

    public void initializeRandomCandidatesSet() {
        originalRequestsFeasibilityAnalysis();
        prepareAndSetRequestsData();
        setRequestRandomParameters();
        initializeCandidates();
    }

    public void setRequestRandomParameters() {
        Random rnd = new Random();
        for (Request request : data.getRequests()) {
            request.setRequestRankingFunction(rnd.nextDouble());
        }
    }

    @Override
    public void localSearch(int localSearchType) {
        switch (localSearchType) {
            case 1:
                this.solution = swapIntraRouteFirstImprovement();
                break;
            case 2:
                this.solution = swapIntraBestImprovement();
                break;
            case 3:
                this.solution = addMinutesInSolutionScheduleFirstImprovement();
                break;
            case 4:
                this.solution = addMinutesInSolutionScheduleBestImprovement();
                break;
            case 5:
                this.solution = removeMinutesInSolutionScheduleFirstImprovement();
                break;
            case 6:
                this.solution = removeMinutesInSolutionScheduleBestImprovement();
                break;
            case 7:
                this.solution = swapInterRouteFirstImprovement();
                break;
            case 8:
                this.solution = swapInterRouteBestImprovement();
                break;
            case 9:
                this.solution = requestReallocationFirstImprovement();
                break;
            case 10:
                this.solution = requestReallocationBestImprovement();
                break;
            case 11:
                this.solution = addRouteFirstImprovement();
                break;
            case 12:
//                this.solution = addRouteBestImprovement();
                break;
        }
        this.solution.buildIntegerRepresentation();
    }
    
    private Solution swapIntraRouteFirstImprovement() {
        Solution solution = new Solution(this.solution);
        for (int i = 0; i < solution.getNumberOfRoutes(); i++) {
            Route route = new Route(solution.getRoute(i));
            long evaluationFunctionBeforeMovement = solution.getEvaluationFunction();
            for (int j = 1; j < route.getIntegerSequenceOfAttendedRequests().size(); j++) {
                for (int k = j + 1; k < route.getIntegerSequenceOfAttendedRequests().size(); k++) {
                    route.swapRequests(j, k, data);
                    actualizeSolution(solution, i, route);
                    long evaluationFunctionAfterMovement = solution.getEvaluationFunction();

                    if (evaluationFunctionAfterMovement < evaluationFunctionBeforeMovement) {
                        return solution;
                    } else {
                        route.swapRequests(j, k, data);
                    }
                }
            }
        }
        return this.solution;
    }

    private void actualizeSolution(Solution solution, int i, Route route) {
        solution.setRoute(i, route);
        solution.calculateEvaluationFunction();
    }

    private Solution swapIntraBestImprovement() {
        Solution solution = new Solution((Solution) this.solution.clone());
        for (int i = 0; i < solution.getNumberOfRoutes(); i++) {
            Route route = new Route(solution.getRoute(i));
            long evaluationFunctionBeforeMovement = solution.getEvaluationFunction();
            for (int j = 1; j < route.getIntegerSequenceOfAttendedRequests().size() - 1; j++) {
                for (int k = j + 1; k < route.getIntegerSequenceOfAttendedRequests().size(); k++) {
                    route.swapRequests(j, k, data);
                    actualizeSolution(solution, i, route);
                    long evaluationFunctionAfterMovement = solution.getEvaluationFunction();
                    if (evaluationFunctionAfterMovement > evaluationFunctionBeforeMovement) {
                        route.swapRequests(j, k, data);
                        actualizeSolution(solution, i, route);
                    } else {
                        evaluationFunctionBeforeMovement = evaluationFunctionAfterMovement;
                    }
                }
            }
        }
        if (solution.getEvaluationFunction() < this.solution.getEvaluationFunction()) {
            return solution;
        } else {
            return this.solution;
        }
    }

    private Solution addMinutesInSolutionScheduleFirstImprovement() {
        Solution solution = new Solution(this.solution);

        for (int i = 0; i < solution.getNumberOfRoutes(); i++) {
            for (int j = 1; j <= 5; j++) {
                Route route = new Route(solution.getRoute(i));
                long evaluationFunctionBeforeMovement = solution.getEvaluationFunction();

                route.addMinutesInRoute(j, data);
                actualizeSolution(solution, i, route);
                long evaluationFunctionAfterMovement = solution.getEvaluationFunction();

                if (evaluationFunctionAfterMovement < evaluationFunctionBeforeMovement) {
                    return solution;
                } else {
                    route.removeMinutesInRoute(j, data);
                }
            }
        }
        return this.solution;
    }

    private Solution addMinutesInSolutionScheduleBestImprovement() {
        Solution solution = new Solution(this.solution);

        for (int i = 0; i < solution.getNumberOfRoutes(); i++) {
            for (int j = 1; j <= 5; j++) {
                Route route = new Route(solution.getRoute(i));
                long evaluationFunctionBeforeMovement = solution.getEvaluationFunction();

                route.addMinutesInRoute(j, data);
                actualizeSolution(solution, i, route);
                long evaluationFunctionAfterMovement = solution.getEvaluationFunction();

                if (evaluationFunctionAfterMovement > evaluationFunctionBeforeMovement) {
                    route.removeMinutesInRoute(j, data);
                    actualizeSolution(solution, i, route);
                }
            }
        }
        if (solution.getEvaluationFunction() < this.solution.getEvaluationFunction()) {
            return solution;
        } else {
            return this.solution;
        }
    }

    private Solution removeMinutesInSolutionScheduleBestImprovement() {
        Solution solution = new Solution(this.solution);

        for (int i = 0; i < solution.getNumberOfRoutes(); i++) {
            for (int j = 1; j <= 5; j++) {
                Route route = new Route(solution.getRoute(i));
                long evaluationFunctionBeforeMovement = solution.getEvaluationFunction();

                route.removeMinutesInRoute(j, data);
                actualizeSolution(solution, i, route);
                long evaluationFunctionAfterMovement = solution.getEvaluationFunction();

                if (evaluationFunctionAfterMovement < evaluationFunctionBeforeMovement) {
                    return solution;
                } else {
                    route.addMinutesInRoute(j, data);
                }
            }
        }
        return this.solution;
    }

    private Solution removeMinutesInSolutionScheduleFirstImprovement() {
        Solution solution = new Solution(this.solution);

        for (int i = 0; i < solution.getNumberOfRoutes(); i++) {
            for (int j = 1; j <= 5; j++) {
                Route route = new Route(solution.getRoute(i));
                long evaluationFunctionBeforeMovement = solution.getEvaluationFunction();

                route.removeMinutesInRoute(j, data);
                actualizeSolution(solution, i, route);
                long evaluationFunctionAfterMovement = solution.getEvaluationFunction();

                if (evaluationFunctionAfterMovement > evaluationFunctionBeforeMovement) {
                    route.addMinutesInRoute(j, data);
                    actualizeSolution(solution, i, route);
                }
            }
        }
        if (solution.getEvaluationFunction() < this.solution.getEvaluationFunction()) {
            return solution;
        } else {
            return this.solution;
        }
    }

    private Solution swapInterRouteFirstImprovement() {
        Solution solution = new Solution(this.solution);

        for (int i = 0; i < solution.getNumberOfRoutes(); i++) {
            long evaluationFunctionBeforeMovement = solution.getEvaluationFunction();
            LinkedHashSet<Integer> firstRoute = new LinkedHashSet<>();
            firstRoute.addAll(returnUsedIds(solution, i));

            for (int j = i + 1; j < solution.getNumberOfRoutes(); j++) {
                LinkedHashSet<Integer> secondRoute = new LinkedHashSet<>();
                secondRoute.addAll(returnUsedIds(solution, j));

                for (int firstId : firstRoute) {
                    for (int secondId : secondRoute) {
                        solution.getRoute(i).replaceRequest(firstId, secondId, data);
                        solution.getRoute(j).replaceRequest(secondId, firstId, data);
                        solution.calculateEvaluationFunction();
                        long evaluationFunctionAfterMovement = solution.getEvaluationFunction();
                        if (evaluationFunctionAfterMovement < evaluationFunctionBeforeMovement) {
                            return solution;
                        } else {
                            solution.getRoute(i).replaceRequest(secondId, firstId, data);
                            solution.getRoute(j).replaceRequest(firstId, secondId, data);
                            solution.calculateEvaluationFunction();
                        }
                    }
                }
            }
        }

        return this.solution;
    }

    private static List<Integer> returnUsedIds(Solution solution, int routePosition) {
        Set<Integer> setOfIds = solution.getRoute(routePosition).getIntegerRouteRepresetation()
                .stream()
                .filter(u -> u.intValue() > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Integer> idsUsed = new ArrayList<>();
        for (int id : setOfIds) {
            idsUsed.add(id);
        }
        return idsUsed;
    }

    private Solution swapInterRouteBestImprovement() {
        Solution solution = new Solution(this.solution);

        for (int i = 0; i < solution.getNumberOfRoutes(); i++) {
            long evaluationFunctionBeforeMovement = solution.getEvaluationFunction();
            List<Integer> firstRoute = new ArrayList<>();
            firstRoute.addAll(returnUsedIds(solution, i));

            for (int j = i + 1; j < solution.getNumberOfRoutes(); j++) {
                List<Integer> secondRoute = new ArrayList<>();
                secondRoute.addAll(returnUsedIds(solution, j));

                for (int k = 0; k < firstRoute.size(); k++) {
                    for (int l = 0; l < secondRoute.size(); l++) {

                        swapRequestsInDifferentRoutes(solution, i, firstRoute, k, secondRoute, l, j);
                        long evaluationFunctionAfterMovement = solution.getEvaluationFunction();

//                        solution.getRoutes().forEach(System.out::println);
                        if (evaluationFunctionAfterMovement > evaluationFunctionBeforeMovement) {
                            swapRequestsInDifferentRoutes(solution, i, secondRoute, l, firstRoute, k, j);
                        } else {
                            //System.out.println("iteration = " + solution);
                            evaluationFunctionBeforeMovement = evaluationFunctionAfterMovement;
                            int removedIdFromFirstRoute = firstRoute.get(k);
                            int removedIdFromSecondRoute = secondRoute.get(l);
                            firstRoute.set(k, removedIdFromSecondRoute);
                            secondRoute.set(l, removedIdFromFirstRoute);
                        }
                    }
                }
            }
        }

        if (solution.getEvaluationFunction() < this.solution.getEvaluationFunction()) {
            return (Solution) solution.clone();
        } else {
            return (Solution) this.solution.clone();
        }
    }

    private void swapRequestsInDifferentRoutes(Solution solution1, int i, List<Integer> firstRoute, int k, List<Integer> secondRoute, int l, int j) {
        solution1.getRoute(i).replaceRequest(firstRoute.get(k), secondRoute.get(l), data);
        solution1.getRoute(j).replaceRequest(secondRoute.get(l), firstRoute.get(k), data);
        solution1.calculateEvaluationFunction();
    }

    private Solution requestReallocationFirstImprovement() {
        Solution solution = new Solution(this.solution);

        for (int i = 0; i < solution.getNumberOfRoutes(); i++) {
            Route firstRoute = new Route(solution.getRoute(i));
            long evaluationFunctionBeforeMovement = solution.getEvaluationFunction();

            List<Integer> firstRouteIdSequence = new ArrayList<>();
            firstRouteIdSequence.addAll(returnUsedIds(solution, i));

            for (int j = 0; j < solution.getNumberOfRoutes(); j++) {
                if (i != j) {
                    Route secondRoute = new Route(solution.getRoute(j));
                    for (int k = 0; k < firstRouteIdSequence.size(); k++) {
                        int requestId = firstRouteIdSequence.get(k);
                        List<Integer> idSequenceToInsertRequest = new ArrayList<>();
                        idSequenceToInsertRequest.addAll(secondRoute.getIntegerSequenceOfAttendedRequests());

                        for (int l = 1; l < idSequenceToInsertRequest.size(); l++) {
                            for (int m = l + 1; m < idSequenceToInsertRequest.size() + 1; m++) {
                                List<Integer> newIdSequence = new ArrayList<>();

                                insertIdInNewSequence(newIdSequence, idSequenceToInsertRequest, l, requestId, m);

                                secondRoute.clear();
                                secondRoute.rebuild(newIdSequence, data);

                                actualizeSolution(solution, j, secondRoute);

                                long evaluationFunctionAfterMovement = solution.getEvaluationFunction();

                                if (evaluationFunctionAfterMovement < evaluationFunctionBeforeMovement) {
                                    //System.out.println("entrou");
                                    return solution;
                                } else {
                                    secondRoute.removeReallocatedRequest(requestId, data);
                                    actualizeSolution(solution, j, secondRoute);
                                }
                            }
                        }
                    }
                }
            }
        }
        return this.solution;
    }

    private void insertIdInNewSequence(List<Integer> newIdSequence, List<Integer> idSequenceToInsertRequest,
            int l, int requestId, int m) {
        newIdSequence.addAll(idSequenceToInsertRequest.subList(0, l));
        newIdSequence.add(requestId);
        newIdSequence.addAll(idSequenceToInsertRequest.subList(l, m - 1));
        newIdSequence.add(requestId);
        newIdSequence.addAll(idSequenceToInsertRequest.subList(m - 1, idSequenceToInsertRequest.size()));
    }

    private Solution requestReallocationBestImprovement() {
        Solution solution = new Solution(this.solution);

        for (int i = 0; i < solution.getNumberOfRoutes(); i++) {
            Route firstRoute = new Route(solution.getRoute(i));
            long evaluationFunctionBeforeMovement = solution.getEvaluationFunction();

            List<Integer> firstRouteIdSequence = new ArrayList<>();
            firstRouteIdSequence.addAll(returnUsedIds(solution, i));

            for (int j = 0; j < solution.getRoutes().size(); j++) {
                if (i != j) {
                    Route secondRoute = new Route(solution.getRoute(j));
                    if (firstRouteIdSequence.size() != 0) {
                        for (int k = 0; k < firstRouteIdSequence.size(); k++) {
                            int requestId = firstRouteIdSequence.get(k);
                            List<Integer> idSequenceToInsertRequest = new ArrayList<>();
                            idSequenceToInsertRequest.addAll(secondRoute.getIntegerSequenceOfAttendedRequests());

                            for (int l = 1; l < idSequenceToInsertRequest.size(); l++) {
                                for (int m = l + 1; m < idSequenceToInsertRequest.size() + 1; m++) {
                                    List<Integer> newIdSequence = new ArrayList<>();

                                    insertIdInNewSequence(newIdSequence, idSequenceToInsertRequest, l, requestId, m);

                                    secondRoute.clear();
                                    secondRoute.rebuild(newIdSequence, data);

                                    actualizeSolution(solution, j, secondRoute);

                                    long evaluationFunctionAfterMovement = solution.getEvaluationFunction();

                                    if (evaluationFunctionAfterMovement > evaluationFunctionBeforeMovement) {
                                        secondRoute.removeReallocatedRequest(requestId, data);
                                        actualizeSolution(solution, j, secondRoute);
                                    } else if (firstRouteIdSequence.size() > 0) {
                                        firstRouteIdSequence.remove(k);
                                        k = 0;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (solution.getEvaluationFunction() < this.solution.getEvaluationFunction()) {
            return solution;
        } else {
            return this.solution;
        }
    }

    private Solution addRouteFirstImprovement() {
        Solution solution = new Solution((Solution) this.solution.clone());
        for (int i = 0; i < solution.getNumberOfRoutes(); i++) {
            Route firstRoute = new Route(solution.getRoute(i));
            long evaluationFunctionBeforeMovement = solution.getEvaluationFunction();
            for (int j = 0; j < solution.getNumberOfRoutes(); j++) {
                if (i != j) {
                    Route secondRoute = new Route(solution.getRoute(j));
                    List<Integer> initialSequence = new ArrayList<>();
                    List<Integer> finalSequence = new ArrayList<>();
                    List<Integer> newSequence = new ArrayList<>();
                    initialSequence.addAll(secondRoute.getIntegerSequenceOfAttendedRequests());
                    initialSequence.remove(initialSequence.size() - 1);
                    finalSequence.addAll(firstRoute.getIntegerSequenceOfAttendedRequests());
                    finalSequence.remove(0);
                    //initialSequence.addAll(finalSequence);
                    newSequence.addAll(initialSequence);
                    newSequence.addAll(finalSequence);
                    System.out.println(newSequence);
                    secondRoute.clear();
                    secondRoute.rebuild(newSequence, data);

                    System.out.println(secondRoute.getIntegerRouteRepresetation());

                    actualizeSolution(solution, j, secondRoute);

                    long evaluationFunctionAfterMovement = solution.getEvaluationFunction();
                    //System.out.println(evaluationFunctionAfterMovement - evaluationFunctionBeforeMovement);
                    if (evaluationFunctionAfterMovement < evaluationFunctionBeforeMovement) {
                        solution.getRoutes().remove(i);
                        i = 0;
                        System.out.println("entrou");
                        return solution;
                    } else {
                        secondRoute.removeAddedRequests(finalSequence, data);
                        actualizeSolution(solution, j, secondRoute);
                    }
                }
            }
        }
        return this.solution;
    }

    @Override
    public void perturbation(int typeOfPerturbation, int intensity) {
        switch (typeOfPerturbation) {
            case 1:
                this.solution = swapIntraRoutePerturbation(intensity);
                break;

            case 2:
                this.solution = swapInterRoutePerturbation(intensity);
                break;

            case 3:
                this.solution = reallocateRequestPerturbation(intensity);
                break;

            case 4:
                //this.solution = swapInterRouteFirstImprovement();
                break;

            case 5:
                //this.solution = requestReallocationFirstImprovement();
                break;

        }
    }

    private Solution swapIntraRoutePerturbation(int intensity) {
        Solution solution = new Solution(this.solution);

        int routeIndex = generateRouteIndex(solution);
        Route route = new Route(solution.getRoute(routeIndex));

        for (int i = 0; i < intensity; i++) {
            List<Integer> positions = generateTwoDiffentRouteRequests(solution, routeIndex);
            int firstRequestIndex = positions.get(0);
            int secondRequestIndex = positions.get(1);

            route.swapRequests(firstRequestIndex, secondRequestIndex, data);
            actualizeSolution(solution, routeIndex, route);
        }
        return solution;
    }

    private List<Integer> generateTwoDiffentRouteRequests(Solution solution, int routeIndex) {
        Random rnd = new Random();
        List<Integer> indexes = new ArrayList<>();
        int routeSize = solution.getRoute(routeIndex).getIntegerSequenceOfAttendedRequests().size();
        int firstRequest, secondRequest;
        firstRequest = rnd.nextInt(routeSize - 2) + 1;
        do {
            secondRequest = rnd.nextInt(routeSize - 2) + 1;
        } while (firstRequest == secondRequest && isTheSameRequest(solution, routeIndex, firstRequest, secondRequest));
        indexes.add(firstRequest);
        indexes.add(secondRequest);

        return indexes;
    }

    private List<Integer> generateTwoDiffentRouteRequests(Solution solution, int firstRouteIndex, int secondRouteIndex) {
        Random rnd = new Random();
        List<Integer> indexes = new ArrayList<>();
        int firstRouteSize = solution.getRoute(firstRouteIndex).getIntegerSequenceOfAttendedRequests().size();
        int secondRouteSize = solution.getRoute(secondRouteIndex).getIntegerSequenceOfAttendedRequests().size();
        int firstRequest, secondRequest;
        firstRequest = rnd.nextInt(firstRouteSize - 2) + 1;
        secondRequest = rnd.nextInt(secondRouteSize - 2) + 1;
        indexes.add(firstRequest);
        indexes.add(secondRequest);

        return indexes;
    }

    private static boolean isTheSameRequest(Solution solution1, int routeIndex, int firstRequest, int secondRequest) {
        return solution1.getRoute(routeIndex).getIntegerSequenceOfAttendedRequests().get(firstRequest)
                .equals(solution1.getRoute(routeIndex).getIntegerSequenceOfAttendedRequests().get(secondRequest));
    }

    private Solution swapInterRoutePerturbation(int intensity) {
        Solution solution = new Solution(this.solution);
        List<Integer> routeIndexes = generateTwoDiffentRouteIndexes(solution);

        int firstRoute = routeIndexes.get(0);
        int secondRoute = routeIndexes.get(1);

        List<Integer> requestIndexes = generateTwoDiffentRouteRequests(solution, firstRoute, secondRoute);
        List<Integer> firstIdSequence = solution.getRoute(firstRoute).getIntegerSequenceOfAttendedRequests();
        List<Integer> secondIdSequence = solution.getRoute(secondRoute).getIntegerSequenceOfAttendedRequests();
        int firstRequestId = firstIdSequence.get(requestIndexes.get(0));
        int secondRequestId = secondIdSequence.get(requestIndexes.get(1));

        solution.getRoute(firstRoute).replaceRequest(firstRequestId, secondRequestId, data);
        solution.getRoute(secondRoute).replaceRequest(secondRequestId, firstRequestId, data);
        solution.calculateEvaluationFunction();

        return solution;
    }

    private List<Integer> generateTwoDiffentRouteIndexes(Solution solution) {
        Random rnd = new Random();
        List<Integer> indexes = new ArrayList<>();
        int totalRoutes = solution.getNumberOfRoutes();
        int firstRoute, secondRoute;
        firstRoute = rnd.nextInt(totalRoutes);
        do {
            secondRoute = rnd.nextInt(totalRoutes);
        } while (firstRoute == secondRoute);
        indexes.add(firstRoute);
        indexes.add(secondRoute);

        return indexes;
    }

    private Integer generateRouteIndex(Solution solution) {
        return new Random().nextInt(solution.getNumberOfRoutes());
    }

    private Solution reallocateRequestPerturbation(int intensity) {
        Solution solution = new Solution(this.solution);
        List<Integer> routeIndexes = generateTwoDiffentRouteIndexes(solution);
        int firstRoute = routeIndexes.get(0);
        int secondRoute = routeIndexes.get(1);

        List<Integer> idSequenceToRemoveRequest = new ArrayList<>();
        List<Integer> idSequenceToInsertRequest = new ArrayList<>();
        idSequenceToRemoveRequest.addAll(returnUsedIds(solution, firstRoute));
        idSequenceToInsertRequest.addAll(solution.getRoute(secondRoute).getIntegerSequenceOfAttendedRequests());

        List<Integer> newIdSequence = new ArrayList<>();
        List<Integer> indexesToRemove = generateTwoDiffentRequestsToOneRoute(idSequenceToRemoveRequest);
        List<Integer> indexesToInsert = generateTwoDiffentRequestsToOneRoute(idSequenceToInsertRequest);
        int firstIndex = indexesToInsert.get(0);
        int secondIndex = indexesToInsert.get(1);
        newIdSequence.addAll(idSequenceToInsertRequest.subList(0, firstIndex));
        newIdSequence.add(idSequenceToRemoveRequest.get(indexesToRemove.get(0)));
        newIdSequence.addAll(idSequenceToInsertRequest.subList(firstIndex, secondIndex - 1));
        newIdSequence.add(idSequenceToRemoveRequest.get(indexesToRemove.get(0)));
        newIdSequence.addAll(idSequenceToInsertRequest.subList(secondIndex - 1, idSequenceToInsertRequest.size()));
        System.out.println(newIdSequence);
//        secondRoute.clear();
//        secondRoute.rebuild(newIdSequence, data);
//
//        solution.setRoute(j, secondRoute);
//        solution.calculateEvaluationFunction();
        return solution;
    }

    private List<Integer> generateTwoDiffentRequestsToOneRoute(List<Integer> idSequence) {
        Random rnd = new Random();
        List<Integer> indexes = new ArrayList<>();
        int routeSize = idSequence.size();
        int firstRequest, secondRequest;
        if (idSequence.get(0) == 0 && idSequence.get(routeSize - 1) == 0) {
            firstRequest = rnd.nextInt(routeSize - 1) + 1;
            secondRequest = rnd.nextInt(routeSize - 1) + 1;
        } else {
            firstRequest = rnd.nextInt(routeSize);
            secondRequest = rnd.nextInt(routeSize);
        }
        indexes.add(firstRequest);
        indexes.add(secondRequest);
        Collections.sort(indexes);

        return indexes;
    }

    private boolean isRouteForOnlyOneRequest(List<Integer> idSequence) {
        int sequenceSize = idSequence.size();
        if (sequenceSize == 4 && idSequence.get(0) == 0 && idSequence.get(sequenceSize - 1) == 0) {
            return true;
        } else if (sequenceSize == 1) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void MultiStart() {

    }

    @Override
    public void SimulatedAnnealing() {

    }

    @Override
    public void VND() {
        buildGreedySolution();
        Solution initialSolution = new Solution(this.getSolution());
        initialSolution.printAllInformations();
        int numberOfNeighborhoods = 10;
        int currentNeighborhood = 1;
        List<Integer> neighborhoods = generateNeighborhoodList(numberOfNeighborhoods);
        while (currentNeighborhood < numberOfNeighborhoods) {
            printAlgorithmInformations(initialSolution, currentNeighborhood);
            localSearch(currentNeighborhood);
            if (solution.getEvaluationFunction() < initialSolution.getEvaluationFunction()) {
                initialSolution.setSolution(solution);
                currentNeighborhood = 1;
            } else {
                currentNeighborhood++;
            }
        }
        solution.setSolution(initialSolution);
        initialSolution.printAllInformations();
    }
    
    private void printAlgorithmInformations(Solution solution, int currentNeighborhood){
        System.out.println("Objective Function = " + solution.getEvaluationFunction() + "\t Neighborhood = " + currentNeighborhood);
    }

    private List<Integer> generateNeighborhoodList(int numberOfNeighborhoods) {
        List<Integer> neighborhoods = new ArrayList<>();
        for (int i = 1; i <= numberOfNeighborhoods; i++) {
            neighborhoods.add(i);
        }
        return neighborhoods;
    }

    @Override
    public void VNS() {

    }

    @Override
    public void GRASP() {

    }

    @Override
    public void ILS() {

    }

    @Override
    public void TabuSearch() {

    }
}
