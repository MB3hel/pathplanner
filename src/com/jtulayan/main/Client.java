package com.jtulayan.main;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.apache.commons.cli.*;

public class Client extends Application {
    private static CommandLine cmd;
    @Override
    public void start(Stage primaryStage) throws Exception {
        Pane root = FXMLLoader.load(getClass().getResource("/com/jtulayan/ui/javafx/MainFXUI.fxml"));
        primaryStage.setTitle("Mercury Motion Profile Generator");
        primaryStage.setScene(new Scene(root));
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    public static void main(String[] args) {
        Options options = new Options();
        CommandLineParser parser = new DefaultParser();

        options.addOption("d", "export-directory", true,"export directory");
        options.addOption("h", "help", false,"display help dialog");
        options.addOption("n", "no-gui", false,"no-gui mode");


        try {
            cmd = parser.parse(options, args);

            if (cmd.hasOption("h")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("motion-profile-generator [OPTIONS]... [TARGETS]...", options);
            }

            if(!cmd.hasOption("n")) {
                launch();
            }
        } catch (Exception e) {
            System.out.println("Oops, something went wrong!");
            e.printStackTrace();
        }
    }

}