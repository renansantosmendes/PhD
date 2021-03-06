/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package VRPDRTSD;

import junit.framework.Assert;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assert.assertNotNull;

/**
 *
 * @author renansantos
 */
public class VRPDRTSD_LocalSearchHeuristics {

    @Test
    public void VND_Test() {
        String instanceName = "r250n12tw10";
        String nodesData = "bh_n12s";
        String adjacenciesData = "bh_adj_n12s";
        int numberOfVehicles = 50;
        int vehicleCapacity = 4;
        System.out.println("Testing Metaheuristics");
        VRPDRTSD problem = new VRPDRTSD(instanceName, nodesData, adjacenciesData, numberOfVehicles, vehicleCapacity);
        problem.vnd();
//        problem.buildGreedySolution();
//        problem.getSolution().printAllInformations();
//        problem.localSearch(4);
        problem.getSolution().printAllInformations();
        assertNotNull(problem.getSolution());
    }

}
