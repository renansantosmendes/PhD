/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package VRPDRTSD;

import InstanceReader.Instance;
import ProblemRepresentation.Route;
import java.io.IOException;
import jxl.read.biff.BiffException;
import org.junit.Test;

/**
 *
 * @author renansantos
 */
public class ReallocationTest {

    @Test
    public void instanceExample() throws IOException, BiffException {
        int requestsNumber = 50;
        String path = "/home/renansantos/Área de Trabalho/Excel Instances/";

        Instance instance = new Instance();
        instance.setNumberOfRequests(requestsNumber)
                .setRequestTimeWindows(10)
                .setInstanceSize("s")
                .setNumberOfNodes(12)
                .setNumberOfVehicles(250)  
                .setVehicleCapacity(4);

        VRPDRTSD problem = new VRPDRTSD(instance, path);
        problem.buildGreedySolution();
        problem.printSolutionInformations();
        //problem.vnd();
        //problem.localSearch(3);
//        problem.localSearch(2);j
        //problem.printSolutionInformations();
        //problem.getSolution().getRoutes().forEach(System.out::println);
    }

    @Test
    public void perturbationTest() throws IOException, BiffException {
        int requestsNumber = 10;

        System.out.println("Testing reallocation perturbation");
        String path = "/home/renansantos/Área de Trabalho/Excel Instances/";

        Instance instance = new Instance();
        instance.setNumberOfRequests(requestsNumber)
                .setRequestTimeWindows(10)
                .setInstanceSize("s")
                .setNumberOfNodes(12)
                .setNumberOfVehicles(250)
                .setVehicleCapacity(4);

        VRPDRTSD problem = new VRPDRTSD(instance, path);
        problem.buildGreedySolution();
        //problem.perturbation(5, 2);
//        problem.printSolutionInformations();
    }
}
