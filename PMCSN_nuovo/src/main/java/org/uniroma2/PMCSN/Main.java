package org.uniroma2.PMCSN;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
import org.uniroma2.PMCSN.controller.RideSharingSystem;
import org.uniroma2.PMCSN.controller.SimpleSystem;
import org.uniroma2.PMCSN.controller.Sistema;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        System.out.println("---- Choose type of system ----");
        System.out.println("0 - Simple ");
        System.out.println("1 - Ride Sharing ");

        int systemType = getChoice();

        Sistema system = null;
        switch (systemType){
            case 0 -> system = new SimpleSystem();
            case 1 -> system = new RideSharingSystem();
            default -> System.out.println("Invalid system choice!");
        }

        System.out.println("---- Choose type of simulation ----");
        System.out.println("0 - Finite horizon simulation ");
        System.out.println("1 - Infinite horizon simulation ");

        int simulationType = getChoice();

        switch (simulationType){
            case 0 -> {
                assert system != null;
                system.runFiniteSimulation();
            }
            case 1 -> {
                assert system != null;
                system.runInfiniteSimulation();
            }
            default -> System.out.println("Invalid simulation choice!");
        }
    }

    private static int getChoice() {
        Scanner input = new Scanner(System.in);
        int choice;

        while (true) {
            System.out.println("Please, make a choice: ");

            choice = input.nextInt();
            if (choice >= 0 && choice <= 1) break;

            System.out.println("Not valid choice!");
        }
        return choice;
    }
}