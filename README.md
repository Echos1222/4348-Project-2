4348 Project 2

This program simulates a bank with multiple tellers and customers using threads and semaphores. Customers randomly decide to deposit or withdraw money. Withdrawals require manager approval. The simulation tracks all transactions until all customers are served and the bank closes.

There are 3 tellers that serve customers concurrently and this program will run through 50 customers. Only two customers will be allowed in the bank at a time. Only 1 teller can interact with the manager at a time and up to 2 tellers can access the safe simultaneously. The format of the interactions is Thread interacts with Thread in what way. For example Teller 0[Customer 45]: going to manager means that the teller thread is telling customer it is going to the manager. So the first one mentioned is the one that is initiating the interaction.

Only one file present called Bank.java

To compile open the command prompt and navigate to the directory containing the file. Then compile the file using javac Bank.java. It will then simulate all 50 customers in the terminal.