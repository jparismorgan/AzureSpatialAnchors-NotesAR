// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
using Microsoft.WindowsAzure.Storage;
using Microsoft.WindowsAzure.Storage.Table;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;

namespace SharingService.Data
{
    internal class AnchorCacheEntity : TableEntity
    {
        public AnchorCacheEntity() { }

        public AnchorCacheEntity(string groupingKey, string anchorId, string partitionKey)
        {
            this.PartitionKey = partitionKey;
            this.RowKey = anchorId;
            this.AnchorId = anchorId;
            this.GroupingKey = groupingKey;
        }

        public string AnchorId { get; set; }

        public string GroupingKey { get; set; }
    }


    internal class CosmosDbCache : IAnchorIdCache
    {
        /// <summary>
        /// We are keeping everything on the same parition for performance. 
        /// You may want to use the groupingKey (or something else) as the partition key in a real app.
        /// </summary>
        private const string partitionKey = "UseARealParititionForProductionCode";

        /// <summary>
        /// The database cache.
        /// </summary>
        private readonly CloudTable dbCache;

        // To ensure our asynchronous initialization code is only ever invoked once, we employ two manualResetEvents
        ManualResetEventSlim initialized = new ManualResetEventSlim();
        ManualResetEventSlim initializing = new ManualResetEventSlim();

        private async Task InitializeAsync()
        {
            if (!this.initialized.Wait(0))
            {
                if (!this.initializing.Wait(0))
                {
                    this.initializing.Set();
                    await this.dbCache.CreateIfNotExistsAsync();
                    this.initialized.Set();
                }

                this.initialized.Wait();
            }
        }

        public CosmosDbCache(string storageConnectionString)
        {
            CloudStorageAccount storageAccount = CloudStorageAccount.Parse(storageConnectionString);
            CloudTableClient tableClient = storageAccount.CreateCloudTableClient();
            this.dbCache = tableClient.GetTableReference("AnchorCache");
        }

        /// <summary>
        /// Gets anchor ids asynchronously.
        /// </summary>
        /// <param name="groupingKey">Get anchor ids with this grouping key.</param>
        /// <exception cref="KeyNotFoundException"></exception>
        /// <returns>A list of anchor ids stored if available; otherwise, null.</returns>
        public async Task<string[]> GetAnchorIdsAsync(string groupingKey)
        {
            await InitializeAsync();

            List<AnchorCacheEntity> results = new List<AnchorCacheEntity>();
            TableQuery<AnchorCacheEntity> tableQuery = new TableQuery<AnchorCacheEntity>()
                .Where( TableQuery.GenerateFilterCondition("GroupingKey", QueryComparisons.Equal, groupingKey));
            TableQuerySegment<AnchorCacheEntity> previousSegment = null;
            while (previousSegment == null || previousSegment.ContinuationToken != null)
            {
                TableQuerySegment<AnchorCacheEntity> currentSegment = await this.dbCache.ExecuteQuerySegmentedAsync<AnchorCacheEntity>(tableQuery, previousSegment?.ContinuationToken);
                previousSegment = currentSegment;
                results.AddRange(previousSegment.Results);
            }

            if (results.Count == 0)
            {
                throw new KeyNotFoundException($"No anchors with {nameof(groupingKey)} {groupingKey} could be found.");
            }

            string[] res = new string[results.Count];
            for (var i = 0; i < results.Count; i++)
            {
                res[i] = results[i].AnchorId;
            }
            return res;
        }

        /// <summary>
        /// Sets the anchor key asynchronously.
        /// </summary>
        /// <param name="groupingKey">The grouping key between several anchors.</param>
        /// <param name="anchorId">The anchor id.</param>
        /// <returns>A Task</returns>
        public async Task SetAnchorIdAsync(string groupingKey, string anchorId)
        {
            await InitializeAsync();

            AnchorCacheEntity anchorEntity = new AnchorCacheEntity(groupingKey, anchorId, CosmosDbCache.partitionKey);

            await this.dbCache.ExecuteAsync(TableOperation.Insert(anchorEntity));

            return;
        }
    }
}
