const databaseName = process.env.MONGO_INITDB_DATABASE;
const username = process.env.MONEW_MONGODB_APP_USERNAME;
const password = process.env.MONEW_MONGODB_APP_PASSWORD;

if (!databaseName || !username || !password) {
  throw new Error('MongoDB application database and credentials are required');
}

const applicationDatabase = db.getSiblingDB(databaseName);

if (applicationDatabase.getUser(username) === null) {
  applicationDatabase.createUser({
    user: username,
    pwd: password,
    roles: [{ role: 'readWrite', db: databaseName }],
  });
}
