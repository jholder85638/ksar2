/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.atomique.ksar;

import java.beans.PropertyVetoException;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JDesktopPane;
import net.atomique.ksar.Export.FileCSV;
import net.atomique.ksar.Export.FilePDF;
import net.atomique.ksar.Graph.Graph;
import static net.atomique.ksar.Main.cmdline;
import net.atomique.ksar.UI.DataView;
import net.atomique.ksar.UI.SortedTreeNode;
import net.atomique.ksar.UI.TreeNodeInfo;

/**
 *
 * @author Max
 */
public class kSar {

    public kSar(JDesktopPane DesktopPane) {
        dataview = new DataView(this);
        dataview.toFront();
        dataview.setVisible(true);
        dataview.setTitle("Empty");
        DesktopPane.add(dataview);
        try {
            int num = DesktopPane.getAllFrames().length;
            if (num != 1) {
                dataview.reshape(5 * num, 5 * num, 800, 600);
            } else {
                dataview.reshape(0, 0, 800, 600);
            }
            dataview.setSelected(true);
        } catch (PropertyVetoException vetoe) {
        }
        if (cmdline.hasOption("input")) {
            do_fileread(cmdline.getOptionValue("input"));
        }
    }

    public kSar() {
    }

    public void do_fileread(String filename) {
        if (filename == null) {
            launched_action = new FileRead(this);
        } else {
            launched_action = new FileRead(this, filename);
        }
        reload_action = ((FileRead) launched_action).get_action();
        do_action();
    }

    public void do_localcommand(String cmd) {
        if (cmd == null) {
            launched_action = new LocalCommand(this);
        } else {
            launched_action = new LocalCommand(this, cmd);
        }
        reload_action = ((LocalCommand) launched_action).get_action();
        do_action();
    }

    public void do_sshread(String cmd) {
        if (cmd == null) {
            launched_action = new SSHCommand(this);
            //mysar.reload_command=t.get_command();
        } else {
            launched_action = new SSHCommand(this, cmd);
        }

        reload_action = ((SSHCommand) launched_action).get_action();
        do_action();
    }

    private void do_action() {
        if (reload_action == null ) {
            System.out.println("action is null");
            return;
        }
        if (launched_action != null) {
            if (dataview != null) {
                dataview.notifyrun(true);
            }
            launched_action.start();
        }
    }

    public int parse(BufferedReader br) {
        String current_line = null;
        long parsing_start = 0L;
        long parsing_end = 0L;
        String[] columns;
        int parser_return = 0;

        parsing_start = System.currentTimeMillis();

        try {
            while ((current_line = br.readLine()) != null && !action_interrupted) {
                Parsing = true;

                lines_parsed++;
                if (current_line.length() == 0) {
                    continue;
                }
                columns = current_line.split("\\s+");

                if (columns.length == 0) {
                    continue;
                }

                String ParserType = columns[0];
                try {
                    Class classtmp = GlobalOptions.getParser(ParserType);
                    if (classtmp != null) {
                        if (myparser == null) {
                            myparser = (AllParser) classtmp.newInstance();
                            myparser.init(this, current_line);

                            continue;
                        } else {
                            if (myparser.getParserName().equals(columns[0])) {
                                myparser.parse_header(current_line);
                                continue;
                            }
                        }
                    }
                } catch (InstantiationException ex) {
                    Logger.getLogger(kSar.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IllegalAccessException ex) {
                    Logger.getLogger(kSar.class.getName()).log(Level.SEVERE, null, ex);
                }


                if (myparser == null) {
                    System.out.println("unknown parser");
                    Parsing = false;
                    return -1;
                }

                parser_return = myparser.parse(current_line, columns, lines_parsed);
                if (parser_return == 1 && GlobalOptions.isDodebug()) {
                    System.out.println("IGNORED (" + lines_parsed + "): " + current_line);
                }
                if (parser_return < 0 && GlobalOptions.isDodebug()) {
                    System.out.println("ERRROR ("+ lines_parsed +"): " + current_line);
                }

                myparser.updateUITitle();
            }
        } catch (IOException ex) {
            Logger.getLogger(kSar.class.getName()).log(Level.SEVERE, null, ex);
            Parsing = false;
        }

        if (dataview != null) {
            dataview.treehome();
            dataview.notifyrun(false);
            dataview.setHasData(true);
        }

        parsing_end = System.currentTimeMillis();
        if (GlobalOptions.isDodebug()) {
            System.out.println("time to parse: " + (parsing_end - parsing_start) + "ms ");
            if (myparser != null) {
                System.out.println("number of datesamples: " + myparser.DateSamples.size());
            }
        }
        Parsing = false;
        
        if (cmdline.hasOption("pdf")) {
            Runnable pdf = new FilePDF(cmdline.getOptionValue("pdf"), this);
            Thread th = new Thread(pdf);
            th.start();
        }
        if (cmdline.hasOption("csv")) {
            Runnable csv = new FileCSV(cmdline.getOptionValue("csv"), this);
            Thread th = new Thread(csv);
            th.start();
        }
        return -1;
    }

    public void cleared() {
        aborted();
    }

    public void aborted() {
        if (dataview != null) {
            System.out.println("reset menu");
            dataview.notifyrun(false);
        }
    }

    public boolean isAction_interrupted() {
        return action_interrupted;
    }

    public void interrupt_parsing() {
        if (isParsing()) {
            action_interrupted = true;
        }
    }

    public void add2tree(SortedTreeNode parent, SortedTreeNode newNode) {
        if (dataview != null) {
            dataview.add2tree(parent, newNode);
        }
    }

    public int get_page_to_print() {
        page_to_print = 0;
        count_printSelected(graphtree);
        return page_to_print;
    }

    public void count_printSelected(SortedTreeNode node) {
        int num = node.getChildCount();

        if (num > 0) {
            Object obj1 = node.getUserObject();
            for (int i = 0; i < num; i++) {
                SortedTreeNode l = (SortedTreeNode) node.getChildAt(i);
                count_printSelected(l);
            }
        } else {
            Object obj1 = node.getUserObject();
            if (obj1 instanceof TreeNodeInfo) {
                TreeNodeInfo tmpnode = (TreeNodeInfo) obj1;
                Graph nodeobj = tmpnode.getNode_object();
                if (nodeobj.isPrintSelected()) {
                    page_to_print++;
                }
            }
        }
    }

    public DataView getDataView() {
        return dataview;
    }

    public boolean isParsing() {
        return Parsing;
    }
    DataView dataview = null;
    private int lines_parsed = 0;
    private String reload_action = "Empty";
    private Thread launched_action = null;
    private boolean action_interrupted = false;
    public AllParser myparser = null;
    private boolean Parsing = false;
    public int total_graph = 0;
    public SortedTreeNode graphtree = new SortedTreeNode("kSar2");
    public int page_to_print = 0;
}
