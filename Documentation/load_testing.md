# Load Testing

## Protocol

Load testing aimed to test load average (5 sec) of Karuta server under high load. Weused a unique testing script :

Load testing script launch for a single user this sequence :

- Log in to backend
- Get entire Portfolio
- Get first node by semantic tag
- Import model to previous node
- Get second node by semantic tag
- Import model to previous node

Parameters we varied to evaluate Load Average 5sec (LOAD) :

- Total number of users (TOTAL_USERS)
- Number of simultaneous users in a pack (NB_USERS_IN_PACK)
- Pause between two packs (in seconds) (PAUSE)

## Results

####PAUSE = 0 second

TOTAL_USERS=50, NB_USERS_IN_PACK=1, PAUSE=0 : LOAD=10-15
TOTAL_USERS=100, NB_USERS_IN_PACK=1, PAUSE=0 : LOAD=30-60

####PAUSE = 1 second

TOTAL_USERS=50, NB_USERS_IN_PACK=1, PAUSE=1 : LOAD= 
20 users : 1.80
50 users : 22

TOTAL_USERS=100, NB_USERS_IN_PACK=1, PAUSE=1 : LOAD= 
90 users : 10 
100 users : 8

TOTAL_USERS=500, NB_USERS_IN_PACK=1, PAUSE=1 : LOAD= 
50 users : 0.5
70 users : 1
100 users : 8
150 users : 23
200 users : 31
250 users : 36
300 users : 36
400 users : 39
500 users : 50   

TOTAL_USERS=1000, NB_USERS_IN_PACK=1, PAUSE=2 : LOAD > 50

####PAUSE =2 seconds

TOTAL_USERS=50, NB_USERS_IN_PACK=1, PAUSE=2 : LOAD= 
20 users : 0.09
50 users : 0.11

TOTAL_USERS=100, NB_USERS_IN_PACK=1, PAUSE=2 : LOAD= 
80 users : 0.7
100 users : 1.8

TOTAL_USERS=500, NB_USERS_IN_PACK=1, PAUSE=2 : LOAD= 
50 users : 0.5
70 users : 0.5
100 users : 1
150 users : 1.5
200 users : 1.6
250 users : 1.6
300 users : 0.75
400 users : 0.85
500 users : 1.10   

TOTAL_USERS=1000, NB_USERS_IN_PACK=1, PAUSE=2 : LOAD < 2

## Impact of load on usability

Here some results of importing model in function of load average (5 sec) :

Load : Importing time

0.5 : 3s
1 : 5s
2 : 8s
3 : 8s
15 : 15-30s
20 : 30s
30 : 40s

## Conclusion

We see that if it exists a minimum of 2 seconds between user login, there is no problem of load.




